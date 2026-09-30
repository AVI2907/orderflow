package com.orderflow.catalog.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    boolean existsByProductIdAndBuyerId(UUID productId, UUID buyerId);

    void deleteByProductId(UUID productId);

    /** One row per reviewed product: [productId, average rating, review count]. */
    @Query("select r.product.id, avg(r.rating), count(r) from Review r group by r.product.id")
    List<Object[]> summarizeByProduct();
}
