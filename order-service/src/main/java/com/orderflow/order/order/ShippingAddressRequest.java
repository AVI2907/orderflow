package com.orderflow.order.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
    @NotBlank @Size(max = 100) String fullName,
    @NotBlank @Size(max = 200) String line1,
    @Size(max = 200) String line2,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 100) String state,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Size(max = 60) String country
) {
    public ShippingAddress toEntity() {
        return new ShippingAddress(
            fullName.trim(),
            line1.trim(),
            line2 == null || line2.isBlank() ? null : line2.trim(),
            city.trim(),
            state.trim(),
            postalCode.trim(),
            country.trim()
        );
    }
}
