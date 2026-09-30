package com.orderflow.catalog.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
    @NotNull UUID sellerId,
    @NotBlank String name,
    String description,
    @NotNull BigDecimal price,
    @NotNull Integer stockQuantity,
    String category,
    String imageUrl
) {}
