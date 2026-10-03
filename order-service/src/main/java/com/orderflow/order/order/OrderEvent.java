package com.orderflow.order.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published to SQS on every package status change. Carries everything the email
 * function needs, so it never has to call back into our services.
 */
public record OrderEvent(
    UUID orderId,
    UUID sellerOrderId,
    UUID sellerId,
    OrderStatus oldStatus,
    OrderStatus newStatus,
    Instant timestamp,
    String buyerEmail,
    String buyerName,
    BigDecimal orderTotal,
    BigDecimal packageSubtotal,
    List<Item> items,
    String carrier,
    String trackingNumber,
    String trackingUrl,
    BigDecimal refundedAmount
) {
    public record Item(String name, int quantity, BigDecimal unitPrice) {}

    public static OrderEvent of(Order order, SellerOrder so, OrderStatus oldStatus, OrderStatus newStatus) {
        return new OrderEvent(
            order.getId(),
            so.getId(),
            so.getSellerId(),
            oldStatus,
            newStatus,
            Instant.now(),
            order.getBuyerEmail(),
            order.getShippingAddress() == null ? null : order.getShippingAddress().getFullName(),
            order.getTotalAmount(),
            so.getSubtotal(),
            so.getItems().stream()
                .map(i -> new Item(i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList(),
            so.getCarrier(),
            so.getTrackingNumber(),
            TrackingLinks.url(so.getCarrier(), so.getTrackingNumber()),
            so.getRefundedAmount()
        );
    }
}
