package com.orderflow.catalog.product;

import com.orderflow.catalog.seller.SellerResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    SellerResponse seller,
    String name,
    String description,
    BigDecimal price,
    Integer stockQuantity,
    String category,
    String imageUrl,
    Instant createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            SellerResponse.from(product.getSeller()),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStockQuantity(),
            product.getCategory(),
            product.getImageUrl(),
            product.getCreatedAt()
        );
    }
}
