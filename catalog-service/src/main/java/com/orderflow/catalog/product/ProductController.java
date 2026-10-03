package com.orderflow.catalog.product;

import com.orderflow.catalog.review.ReviewRepository;
import com.orderflow.catalog.seller.Seller;
import com.orderflow.catalog.seller.SellerRepository;
import com.orderflow.catalog.seller.SellerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/products")
public class ProductController {

    // Only images uploaded to our own bucket, never arbitrary outside links
    private static final Pattern IMAGE_URL = Pattern.compile("^/product-images/[0-9a-f-]{36}\\.(jpg|png|webp)$");

    public record ImageUpdateRequest(String imageUrl) {}

    /** Editable product details. The photo is changed separately, through PATCH /{id}/image. */
    public record ProductUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive java.math.BigDecimal price,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @Size(max = 255) String category
    ) {}

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final ReviewRepository reviewRepository;

    public ProductController(ProductRepository productRepository, SellerRepository sellerRepository,
                             ReviewRepository reviewRepository) {
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
        this.reviewRepository = reviewRepository;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest req, Authentication authentication) {
        // The seller comes from the login token, never from the request body
        Seller seller = sellerRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sellers can create products"));
        if (seller.getStatus() != SellerStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your seller account has not been approved yet");
        }

        Product product = new Product();
        product.setSeller(seller);
        product.setName(req.name());
        product.setDescription(req.description());
        product.setPrice(req.price());
        product.setStockQuantity(req.stockQuantity());
        product.setCategory(req.category());
        product.setImageUrl(cleanImageUrl(req.imageUrl()));

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(ProductResponse.from(saved));
    }

    @GetMapping
    public List<ProductResponse> list() {
        Map<UUID, RatingSummary> ratings = ratingSummaries();
        return productRepository.findAll().stream()
            .filter(p -> p.getSeller().getStatus() == SellerStatus.APPROVED) // hide pending/suspended sellers
            .map(p -> ProductResponse.from(p, ratings.getOrDefault(p.getId(), RatingSummary.NONE)))
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
        Map<UUID, RatingSummary> ratings = ratingSummaries();
        return productRepository.findById(id)
            .filter(p -> p.getSeller().getStatus() == SellerStatus.APPROVED)
            .map(p -> ProductResponse.from(p, ratings.getOrDefault(p.getId(), RatingSummary.NONE)))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/seller/{sellerId}")
    public List<ProductResponse> listBySeller(@PathVariable UUID sellerId) {
        Map<UUID, RatingSummary> ratings = ratingSummaries();
        return productRepository.findBySellerId(sellerId).stream()
            .map(p -> ProductResponse.from(p, ratings.getOrDefault(p.getId(), RatingSummary.NONE)))
            .toList();
    }

    /** Lets the owning seller edit a product's details. */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest req,
                                                  Authentication authentication) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        Seller requester = sellerRepository.findByEmail(authentication.getName()).orElse(null);
        if (requester == null || !requester.getId().equals(product.getSeller().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        product.setName(req.name().trim());
        product.setDescription(req.description());
        product.setPrice(req.price());
        product.setStockQuantity(req.stockQuantity());
        product.setCategory(req.category());
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(ProductResponse.from(saved, ratingSummaries().getOrDefault(id, RatingSummary.NONE)));
    }

    /** Lets the owning seller add, replace or remove a product photo. */
    @PatchMapping("/{id}/image")
    public ResponseEntity<ProductResponse> updateImage(@PathVariable UUID id, @RequestBody ImageUpdateRequest req,
                                                       Authentication authentication) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        Seller requester = sellerRepository.findByEmail(authentication.getName()).orElse(null);
        if (requester == null || !requester.getId().equals(product.getSeller().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        product.setImageUrl(cleanImageUrl(req.imageUrl()));
        Product saved = productRepository.save(product);
        return ResponseEntity.ok(ProductResponse.from(saved, ratingSummaries().getOrDefault(id, RatingSummary.NONE)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            Seller requester = sellerRepository.findByEmail(authentication.getName()).orElse(null);
            if (requester == null || !requester.getId().equals(product.getSeller().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // Reviews reference the product, so they go first
        reviewRepository.deleteByProductId(id);
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String cleanImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        if (!IMAGE_URL.matcher(url).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
        }
        return url;
    }

    /** Average rating and count for every reviewed product, in one query. */
    private Map<UUID, RatingSummary> ratingSummaries() {
        Map<UUID, RatingSummary> map = new HashMap<>();
        for (Object[] row : reviewRepository.summarizeByProduct()) {
            map.put((UUID) row[0], new RatingSummary(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return map;
    }
}
