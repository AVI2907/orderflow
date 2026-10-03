package com.orderflow.order.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerOrderResponse(
    UUID id,
    UUID sellerId,
    BigDecimal subtotal,
    OrderStatus status,
    ShippingAddress shippingAddress,
    List<OrderItemResponse> items,
    String carrier,
    String trackingNumber,
    String trackingUrl,
    Instant paidAt,
    Instant shippedAt,
    Instant deliveredAt,
    Instant createdAt
) {
    public static SellerOrderResponse from(SellerOrder so) {
        return new SellerOrderResponse(
            so.getId(),
            so.getSellerId(),
            so.getSubtotal(),
            so.getStatus(),
            // Sellers need to know where to ship their part of the order
            so.getOrder().getShippingAddress(),
            so.getItems().stream().map(OrderItemResponse::from).toList(),
            so.getCarrier(),
            so.getTrackingNumber(),
            TrackingLinks.url(so.getCarrier(), so.getTrackingNumber()),
            so.getPaidAt(),
            so.getShippedAt(),
            so.getDeliveredAt(),
            so.getCreatedAt()
        );
    }
}
