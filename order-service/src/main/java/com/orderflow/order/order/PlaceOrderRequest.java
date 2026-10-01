package com.orderflow.order.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceOrderRequest(
    @NotEmpty @Size(max = 50) @Valid List<OrderItemRequest> items,
    @NotNull @Valid ShippingAddressRequest shippingAddress
) {}
