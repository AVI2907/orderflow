package com.orderflow.order.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID buyerId,
    BigDecimal totalAmount,
    OrderStatus overallStatus,
    List<SellerOrderResponse> sellerOrders,
    Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getBuyerId(),
            order.getTotalAmount(),
            order.getOverallStatus(),
            order.getSellerOrders().stream().map(SellerOrderResponse::from).toList(),
            order.getCreatedAt()
        );
    }
}
