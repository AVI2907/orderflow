package com.orderflow.catalog.product;

import com.orderflow.catalog.seller.Seller;
import com.orderflow.catalog.seller.SellerStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    SellerSummary seller,
    String name,
    String description,
    BigDecimal price,
    Integer stockQuantity,
    String category,
    String imageUrl,
    double averageRating,
    long reviewCount,
    Instant createdAt
) {
    /** Public seller info shown alongside products. Deliberately no email address. */
    public record SellerSummary(UUID id, String businessName, SellerStatus status) {
        static SellerSummary from(Seller seller) {
            return new SellerSummary(seller.getId(), seller.getBusinessName(), seller.getStatus());
        }
    }

    public static ProductResponse from(Product product) {
        return from(product, RatingSummary.NONE);
    }

    public static ProductResponse from(Product product, RatingSummary rating) {
        return new ProductResponse(
            product.getId(),
            SellerSummary.from(product.getSeller()),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getCategory(),
            product.getImageUrl(),
            Math.round(rating.average() * 10) / 10.0,
            rating.count(),
            product.getCreatedAt()
        );
    }
}
