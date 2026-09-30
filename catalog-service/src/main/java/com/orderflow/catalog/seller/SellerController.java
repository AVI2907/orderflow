package com.orderflow.catalog.seller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sellers")
public class SellerController {

    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;

    public SellerController(SellerRepository sellerRepository, PasswordEncoder passwordEncoder) {
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<SellerResponse> register(@Valid @RequestBody SellerRegistrationRequest req) {
        Seller seller = new Seller();
        seller.setEmail(req.email());
        seller.setPasswordHash(passwordEncoder.encode(req.password()));
        seller.setBusinessName(req.businessName());
        Seller saved = sellerRepository.save(seller);
        return ResponseEntity.ok(SellerResponse.from(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SellerResponse> get(@PathVariable UUID id) {
        return sellerRepository.findById(id)
            .map(SellerResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SellerResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SellerStatusUpdateRequest req) {
        Seller seller = sellerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seller not found: " + id));
        seller.setStatus(req.status());
        Seller saved = sellerRepository.save(seller);
        return ResponseEntity.ok(SellerResponse.from(saved));
    }
}
