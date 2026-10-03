package com.orderflow.order.order;

import com.orderflow.order.catalog.CatalogClient;
import com.orderflow.order.config.SqsEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Cancels packages before they ship. Paid packages are restocked and refunded. */
@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final StripePaymentService paymentService;
    private final CatalogClient catalogClient;
    private final SqsEventPublisher eventPublisher;

    public OrderCancellationService(OrderRepository orderRepository, StripePaymentService paymentService,
                                    CatalogClient catalogClient, SqsEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.catalogClient = catalogClient;
        this.eventPublisher = eventPublisher;
    }

    /** cancelledBy is BUYER, SELLER or ADMIN. Safe to call twice: an already-cancelled package is left alone. */
    @Transactional
    public void cancel(SellerOrder sellerOrder, String cancelledBy) {
        Order order = sellerOrder.getOrder();

        switch (sellerOrder.getStatus()) {
            case CANCELLED -> {
                return;
            }
            case SHIPPED, DELIVERED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This has already shipped, so it can't be cancelled");
            case PLACED -> {
                if ("SELLER".equals(cancelledBy)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the buyer can cancel an unpaid order");
                }
                // Unpaid: cancel the whole order. Nothing was charged and no stock was taken.
                for (SellerOrder so : order.getSellerOrders()) {
                    if (so.getStatus() == OrderStatus.PLACED) {
                        markCancelled(order, so, cancelledBy, null);
                    }
                }
            }
            case PAID -> {
                // 1. Put the items back in stock (catalog-service ignores repeats for the same package)
                List<CatalogClient.StockLine> lines = sellerOrder.getItems().stream()
                        .map(item -> new CatalogClient.StockLine(item.getProductId(), item.getQuantity()))
                        .toList();
                catalogClient.restoreStock(sellerOrder.getId(), lines);

                // 2. Refund this package (Stripe returns the same refund if this is retried)
                sellerOrder.setRefundId(paymentService.refundSellerOrder(order, sellerOrder));

                markCancelled(order, sellerOrder, cancelledBy, sellerOrder.getSubtotal());
            }
        }

        order.setOverallStatus(order.computeOverallStatus());
        orderRepository.save(order);
    }

    private void markCancelled(Order order, SellerOrder so, String cancelledBy, BigDecimal refunded) {
        OrderStatus oldStatus = so.getStatus();
        Instant now = Instant.now();
        so.setStatus(OrderStatus.CANCELLED);
        so.setCancelledAt(now);
        so.setCancelledBy(cancelledBy);
        so.setRefundedAmount(refunded);
        eventPublisher.publish(OrderEvent.of(order, so, oldStatus, OrderStatus.CANCELLED));
    }
}
