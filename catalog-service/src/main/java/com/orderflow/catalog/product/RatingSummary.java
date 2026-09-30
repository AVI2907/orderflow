package com.orderflow.catalog.product;

public record RatingSummary(double average, long count) {
    public static final RatingSummary NONE = new RatingSummary(0, 0);
}
