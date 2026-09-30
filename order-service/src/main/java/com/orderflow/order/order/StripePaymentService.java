package com.orderflow.order.order;

import com.orderflow.order.config.SqsEventPublisher;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class StripePaymentService {

    private final OrderRepository orderRepository;
    private final SqsEventPublisher eventPublisher;
    private final String secretKey;

    public StripePaymentService(
            OrderRepository orderRepository,
            SqsEventPublisher eventPublisher,
            @Value("${stripe.secret-key:}") String secretKey) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.secretKey = secretKey;
    }

    /** Stripe amounts are integers in the smallest currency unit (cents). */
    public static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public String createPaymentIntent(UUID orderId, String callerBuyerId) {
        if (secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payments are not configured");
        }

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

        // Same idempotency key per order: reloading the payment page reuses the same
        // PaymentIntent instead of creating a second one that could also be charged
        RequestOptions options = RequestOptions.builder()
                .setApiKey(secretKey)
                .setIdempotencyKey("order-" + order.getId())
                .build();

        try {
            return PaymentIntent.create(params, options).getClientSecret();
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start payment", e);
        }
    }

    /** Called from the verified Stripe webhook. Safe to call more than once for the same order. */
    @Transactional
    public void markPaid(UUID orderId, long amountReceivedCents) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            System.err.println("Stripe webhook for unknown order " + orderId);
            return;
        }
        if (order.getOverallStatus() != OrderStatus.PLACED) {
            return; // already processed — Stripe can deliver the same event more than once
        }
        if (toCents(order.getTotalAmount()) != amountReceivedCents) {
            System.err.println("Amount mismatch for order " + orderId + ": received " + amountReceivedCents);
            return;
        }

        for (SellerOrder so : order.getSellerOrders()) {
            OrderStatus oldStatus = so.getStatus();
            so.setStatus(OrderStatus.PAID);
            eventPublisher.publish(new OrderEvent(
                    order.getId(), so.getId(), so.getSellerId(), oldStatus, OrderStatus.PAID, Instant.now()));
        }
        order.setOverallStatus(order.computeOverallStatus());
        orderRepository.save(order);
    }
}
