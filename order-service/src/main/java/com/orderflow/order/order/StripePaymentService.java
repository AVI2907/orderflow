package com.orderflow.order.order;

import com.orderflow.order.catalog.CatalogClient;
import com.orderflow.order.config.SqsEventPublisher;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentSearchResult;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentSearchParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StripePaymentService {

    private final OrderRepository orderRepository;
    private final SqsEventPublisher eventPublisher;
    private final CatalogClient catalogClient;
    private final String secretKey;

    public StripePaymentService(
            OrderRepository orderRepository,
            SqsEventPublisher eventPublisher,
            CatalogClient catalogClient,
            @Value("${stripe.secret-key:}") String secretKey) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.catalogClient = catalogClient;
        this.secretKey = secretKey;
    }

    /** Stripe amounts are integers in the smallest currency unit (cents). */
    public static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public String createPaymentIntent(UUID orderId, String callerBuyerId) {
        requireConfigured();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (callerBuyerId == null || !callerBuyerId.equals(order.getBuyerId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
        if (order.getOverallStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order has already been processed");
        }

        // Amount comes from the database, never from the browser
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toCents(order.getTotalAmount()))
                .setCurrency("usd")
                .putMetadata("orderId", order.getId().toString())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .build();

        try {
            // Same idempotency key per order: reloading the payment page reuses the same
            // PaymentIntent instead of creating a second one that could also be charged
            return PaymentIntent.create(params, options("order-" + order.getId())).getClientSecret();
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start payment", e);
        }
    }

    /** Called from the verified Stripe webhook. Safe to call more than once for the same order. */
    @Transactional
    public void markPaid(UUID orderId, long amountReceivedCents, String paymentIntentId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            System.err.println("Stripe webhook for unknown order " + orderId);
            return;
        }
        if (order.getOverallStatus() == OrderStatus.CANCELLED) {
            // Paid after the buyer cancelled (e.g. a payment page left open in another tab): give the money back
            refundCancelledOrder(order, paymentIntentId);
            return;
        }
        if (order.getOverallStatus() != OrderStatus.PLACED) {
            return; // already processed: Stripe can deliver the same event more than once
        }
        if (toCents(order.getTotalAmount()) != amountReceivedCents) {
            System.err.println("Amount mismatch for order " + orderId + ": received " + amountReceivedCents);
            return;
        }

        order.setPaymentIntentId(paymentIntentId);

        // Reduce stock first. If this fails, the exception rolls everything back and Stripe
        // retries the webhook later; catalog-service ignores repeats for the same order.
        List<CatalogClient.StockLine> stockLines = order.getSellerOrders().stream()
                .flatMap(so -> so.getItems().stream())
                .map(item -> new CatalogClient.StockLine(item.getProductId(), item.getQuantity()))
                .toList();
        List<UUID> oversold = catalogClient.deductStock(order.getId(), stockLines);
        if (!oversold.isEmpty()) {
            System.err.println("OVERSOLD: order " + orderId + " products " + oversold + " - needs seller attention");
        }

        for (SellerOrder so : order.getSellerOrders()) {
            OrderStatus oldStatus = so.getStatus();
            so.setStatus(OrderStatus.PAID);
            so.setPaidAt(Instant.now());
            eventPublisher.publish(new OrderEvent(
                    order.getId(), so.getId(), so.getSellerId(), oldStatus, OrderStatus.PAID, Instant.now()));
        }
        order.setOverallStatus(order.computeOverallStatus());
        orderRepository.save(order);
    }

    /**
     * Refunds one seller's part of a paid order and returns the Stripe refund ID.
     * Safe to retry: Stripe returns the same refund for the same idempotency key.
     */
    public String refundSellerOrder(Order order, SellerOrder sellerOrder) {
        requireConfigured();
        try {
            String paymentIntentId = order.getPaymentIntentId() != null
                    ? order.getPaymentIntentId()
                    : findPaymentIntentId(order);
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(toCents(sellerOrder.getSubtotal()))
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("sellerOrderId", sellerOrder.getId().toString())
                    .build();
            return Refund.create(params, options("refund-" + sellerOrder.getId())).getId();
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The refund could not be processed. Please try again.", e);
        }
    }

    private void refundCancelledOrder(Order order, String paymentIntentId) {
        if (paymentIntentId == null) return;
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("reason", "order was cancelled before payment completed")
                    .build();
            Refund.create(params, options("refund-order-" + order.getId()));
            order.setPaymentIntentId(paymentIntentId);
            orderRepository.save(order);
            System.err.println("Refunded a payment that arrived after order " + order.getId() + " was cancelled");
        } catch (StripeException e) {
            // Failing the webhook makes Stripe retry it later
            throw new IllegalStateException("Could not refund payment for cancelled order " + order.getId(), e);
        }
    }

    /** For orders paid before payment IDs were saved: find the payment by the order ID in its metadata. */
    private String findPaymentIntentId(Order order) throws StripeException {
        PaymentIntentSearchParams params = PaymentIntentSearchParams.builder()
                .setQuery("metadata['orderId']:'" + order.getId() + "' AND status:'succeeded'")
                .build();
        PaymentIntentSearchResult result = PaymentIntent.search(params, options(null));
        if (result.getData().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No payment was found for this order");
        }
        String id = result.getData().get(0).getId();
        order.setPaymentIntentId(id);
        return id;
    }

    private RequestOptions options(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder().setApiKey(secretKey);
        if (idempotencyKey != null) builder.setIdempotencyKey(idempotencyKey);
        return builder.build();
    }

    private void requireConfigured() {
        if (secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payments are not configured");
        }
    }
}
