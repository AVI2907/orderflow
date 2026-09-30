package com.orderflow.catalog.seller;

import jakarta.validation.constraints.NotNull;

public record SellerStatusUpdateRequest(@NotNull SellerStatus status) {}
