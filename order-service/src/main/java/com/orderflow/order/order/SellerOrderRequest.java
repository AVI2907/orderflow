package com.orderflow.order.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SellerOrderRequest(
    @NotNull UUID sellerId,
    @NotEmpty @Valid List<OrderItemRequest> items
) {}
