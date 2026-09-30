package com.orderflow.order.order;

import jakarta.validation.constraints.NotNull;

public record SellerOrderStatusUpdateRequest(@NotNull OrderStatus status) {}
