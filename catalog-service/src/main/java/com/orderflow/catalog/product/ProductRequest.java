package com.orderflow.catalog.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// No sellerId: the seller is always taken from the login token
public record ProductRequest(
    @NotBlank String name,
    String description,
    @NotNull @Positive BigDecimal price,
    @NotNull @PositiveOrZero Integer stockQuantity,
    String category,
    String imageUrl
) {}
