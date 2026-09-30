package com.orderflow.catalog.config;

import com.orderflow.catalog.buyer.BuyerRepository;
import com.orderflow.catalog.seller.SellerRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SellerRepository sellerRepository;
    private final BuyerRepository buyerRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            SellerRepository sellerRepository,
            BuyerRepository buyerRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.sellerRepository = sellerRepository;
        this.buyerRepository = buyerRepository;
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record LoginResponse(String token) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_BUYER")
                .replace("ROLE_", "");

        UUID sellerId = null;
        UUID buyerId = null;
        if ("SELLER".equals(role)) {
            sellerId = sellerRepository.findByEmail(req.email())
                    .map(seller -> seller.getId())
                    .orElse(null);
        } else if ("BUYER".equals(role)) {
            buyerId = buyerRepository.findByEmail(req.email())
                    .map(buyer -> buyer.getId())
                    .orElse(null);
        }

        String token = jwtService.generateToken(req.email(), role, sellerId, buyerId);
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
