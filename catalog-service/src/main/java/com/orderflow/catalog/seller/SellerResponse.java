package com.orderflow.catalog.seller;

import java.time.Instant;
import java.util.UUID;

public record SellerResponse(
    UUID id,
    String email,
    String businessName,
    SellerStatus status,
    Instant createdAt
) {
    public static SellerResponse from(Seller seller) {
        return new SellerResponse(
            seller.getId(),
            seller.getEmail(),
            seller.getBusinessName(),
            seller.getStatus(),
            seller.getCreatedAt()
        );
    }
}
