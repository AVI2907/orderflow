package com.orderflow.order.config;

public record AuthenticatedUser(String email, String sellerId, String buyerId) {}
