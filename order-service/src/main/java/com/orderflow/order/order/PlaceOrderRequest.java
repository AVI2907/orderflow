package com.orderflow.order.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlaceOrderRequest(
    @NotEmpty @Valid List<SellerOrderRequest> sellerOrders,
    @NotNull @Valid ShippingAddressRequest shippingAddress
) {}
