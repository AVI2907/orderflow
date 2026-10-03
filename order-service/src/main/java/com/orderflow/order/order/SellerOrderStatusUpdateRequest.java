package com.orderflow.order.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** carrier and trackingNumber are required when status is SHIPPED, and ignored otherwise. */
public record SellerOrderStatusUpdateRequest(
    @NotNull OrderStatus status,
    @Size(max = 20) String carrier,
    @Size(max = 64) String trackingNumber
) {}
