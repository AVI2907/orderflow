package com.orderflow.catalog.review;

import com.orderflow.catalog.buyer.Buyer;
import com.orderflow.catalog.buyer.BuyerRepository;
import com.orderflow.catalog.product.Product;
import com.orderflow.catalog.product.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final BuyerRepository buyerRepository;

    public ReviewController(ReviewRepository reviewRepository, ProductRepository productRepository,
                            BuyerRepository buyerRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.buyerRepository = buyerRepository;
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {}

    public record ReviewResponse(UUID id, UUID buyerId, String buyerName, int rating, String comment, Instant createdAt) {
        static ReviewResponse from(Review r) {
            return new ReviewResponse(r.getId(), r.getBuyerId(), r.getBuyerName(), r.getRating(), r.getComment(), r.getCreatedAt());
        }
    }

    @GetMapping
    public List<ReviewResponse> list(@PathVariable UUID productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream().map(ReviewResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID productId,
                                                 @Valid @RequestBody ReviewRequest req,
                                                 Authentication authentication) {
        // Only buyer accounts can review; the buyer comes from the login token
        Buyer buyer = buyerRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only buyers can write reviews"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        if (reviewRepository.existsByProductIdAndBuyerId(productId, buyer.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setBuyerId(buyer.getId());
        review.setBuyerName(buyer.getName());
        review.setRating(req.rating());
        review.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());

        try {
            Review saved = reviewRepository.save(review);
            return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(saved));
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous submissions: the unique constraint catches the second one
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        }
    }
}
