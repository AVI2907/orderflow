package com.orderflow.catalog.product;

import com.orderflow.catalog.seller.Seller;
import com.orderflow.catalog.seller.SellerRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;

    public ProductController(ProductRepository productRepository, SellerRepository sellerRepository) {
        this.productRepository = productRepository;
        this.sellerRepository = sellerRepository;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
        Seller seller = sellerRepository.findById(req.sellerId())
            .orElseThrow(() -> new IllegalArgumentException("Seller not found: " + req.sellerId()));

        Product product = new Product();
        product.setSeller(seller);
        product.setName(req.name());
        product.setDescription(req.description());
        product.setPrice(req.price());
        product.setStockQuantity(req.stockQuantity());
        product.setCategory(req.category());
        product.setImageUrl(req.imageUrl());

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(ProductResponse.from(saved));
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
        return productRepository.findById(id)
            .map(ProductResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/seller/{sellerId}")
    public List<ProductResponse> listBySeller(@PathVariable UUID sellerId) {
        return productRepository.findBySellerId(sellerId).stream().map(ProductResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            String email = authentication.getName();
            Seller requester = sellerRepository.findByEmail(email).orElse(null);
            if (requester == null || !requester.getId().equals(product.getSeller().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
