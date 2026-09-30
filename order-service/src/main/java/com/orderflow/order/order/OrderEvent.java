package com.orderflow.order.order;

import java.time.Instant;
import java.util.UUID;

public record OrderEvent(
    UUID orderId,
    UUID sellerOrderId,
    UUID sellerId,
    OrderStatus oldStatus,
    OrderStatus newStatus,
    Instant timestamp
) {}
