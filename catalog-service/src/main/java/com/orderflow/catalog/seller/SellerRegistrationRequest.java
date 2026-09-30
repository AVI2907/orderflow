package com.orderflow.catalog.seller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SellerRegistrationRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotBlank String businessName
) {}
