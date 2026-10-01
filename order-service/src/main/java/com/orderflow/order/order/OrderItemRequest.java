package com.orderflow.order.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The browser only says which product and how many. Price, name and seller come from catalog-service. */
public record OrderItemRequest(
    @NotNull UUID productId,
    @NotNull @Min(1) @Max(100) Integer quantity
) {}
