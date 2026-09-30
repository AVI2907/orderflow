package com.orderflow.catalog.buyer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/buyers")
public class BuyerController {

    private final BuyerRepository buyerRepository;
    private final PasswordEncoder passwordEncoder;

    public BuyerController(BuyerRepository buyerRepository, PasswordEncoder passwordEncoder) {
        this.buyerRepository = buyerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String name
    ) {}

    public record BuyerResponse(UUID id, String email, String name, Instant createdAt) {
        static BuyerResponse from(Buyer b) {
            return new BuyerResponse(b.getId(), b.getEmail(), b.getName(), b.getCreatedAt());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<BuyerResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (buyerRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        Buyer buyer = new Buyer();
        buyer.setEmail(req.email());
        buyer.setPasswordHash(passwordEncoder.encode(req.password()));
        buyer.setName(req.name());

        Buyer saved = buyerRepository.save(buyer);
        return ResponseEntity.ok(BuyerResponse.from(saved));
    }
}
