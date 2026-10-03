package com.orderflow.catalog.seller;

import com.orderflow.catalog.buyer.BuyerRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sellers")
public class SellerController {

    private final SellerRepository sellerRepository;
    private final BuyerRepository buyerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    public SellerController(SellerRepository sellerRepository, BuyerRepository buyerRepository,
                            PasswordEncoder passwordEncoder, @Value("${admin.email}") String adminEmail) {
        this.sellerRepository = sellerRepository;
        this.buyerRepository = buyerRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
    }

    /** Public sign-up. New sellers start as PENDING and can't list products until an admin approves them. */
    @PostMapping("/register")
    public ResponseEntity<SellerResponse> register(@Valid @RequestBody SellerRegistrationRequest req) {
        String email = req.email().trim().toLowerCase();
        // One email = one account, across sellers, buyers and the admin
        if (sellerRepository.findByEmail(email).isPresent()
                || buyerRepository.existsByEmail(email)
                || email.equalsIgnoreCase(adminEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }

        Seller seller = new Seller();
        seller.setEmail(email);
        seller.setPasswordHash(passwordEncoder.encode(req.password()));
        seller.setBusinessName(req.businessName().trim());
        seller.setStatus(SellerStatus.PENDING);
        Seller saved = sellerRepository.save(seller);
        return ResponseEntity.status(HttpStatus.CREATED).body(SellerResponse.from(saved));
    }

    /** Admin only (see SecurityConfig): every seller, newest first. */
    @GetMapping
    public List<SellerResponse> list() {
        return sellerRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .map(SellerResponse::from)
            .toList();
    }

    /** The logged-in seller's own account, including whether it's been approved. */
    @GetMapping("/me")
    public SellerResponse me(Authentication authentication) {
        return sellerRepository.findByEmail(authentication.getName())
            .map(SellerResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a seller account"));
    }

    /** A seller's full details (including email): only for that seller or the admin. */
    @GetMapping("/{id}")
    public ResponseEntity<SellerResponse> get(@PathVariable UUID id, Authentication authentication) {
        Seller seller = sellerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !seller.getEmail().equalsIgnoreCase(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(SellerResponse.from(seller));
    }

    /** Admin only (see SecurityConfig): approve, suspend or reactivate a seller. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<SellerResponse> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody SellerStatusUpdateRequest req) {
        Seller seller = sellerRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller not found"));
        seller.setStatus(req.status());
        Seller saved = sellerRepository.save(seller);
        return ResponseEntity.ok(SellerResponse.from(saved));
    }
}
