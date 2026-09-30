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
    List<OrderItemResponse> items,
    Instant createdAt
) {
    public static SellerOrderResponse from(SellerOrder so) {
        return new SellerOrderResponse(
            so.getId(),
            so.getSellerId(),
            so.getSubtotal(),
            so.getStatus(),
            so.getItems().stream().map(OrderItemResponse::from).toList(),
            so.getCreatedAt()
        );
    }
}
