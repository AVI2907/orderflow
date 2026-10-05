package com.orderflow.catalog.buyer;

import com.orderflow.catalog.seller.SellerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** The logged-in buyer's own profile. Always the account in the login token, never an ID from the browser. */
@RestController
@RequestMapping("/buyers/me")
public class BuyerProfileController {

    private final BuyerRepository buyerRepository;
    private final SellerRepository sellerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;

    public BuyerProfileController(BuyerRepository buyerRepository, SellerRepository sellerRepository,
                                  PasswordEncoder passwordEncoder, @Value("${admin.email}") String adminEmail) {
        this.buyerRepository = buyerRepository;
        this.sellerRepository = sellerRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
    }

    public record AddressDto(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country
    ) {
        static AddressDto from(BuyerAddress a) {
            if (a == null || a.getLine1() == null) return null;
            return new AddressDto(a.getFullName(), a.getLine1(), a.getLine2(), a.getCity(),
                                  a.getState(), a.getPostalCode(), a.getCountry());
        }

        BuyerAddress toEntity() {
            String l2 = line2 == null || line2.isBlank() ? null : line2.trim();
            return new BuyerAddress(fullName.trim(), line1.trim(), l2, city.trim(),
                                    state.trim(), postalCode.trim(), country.trim());
        }
    }

    public record ProfileResponse(UUID id, String email, String name, String phone,
                                  AddressDto defaultAddress, Instant createdAt) {
        static ProfileResponse from(Buyer b) {
            return new ProfileResponse(b.getId(), b.getEmail(), b.getName(), b.getPhone(),
                                       AddressDto.from(b.getDefaultAddress()), b.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 30) @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "Phone can only contain digits, spaces, +, - and brackets") String phone,
        @Valid AddressDto defaultAddress
    ) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record ChangeEmailRequest(@NotBlank String currentPassword,
                                     @NotBlank @Email @Size(max = 255) String newEmail) {}

    @GetMapping
    public ProfileResponse get(Authentication authentication) {
        return ProfileResponse.from(currentBuyer(authentication));
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest req, Authentication authentication) {
        Buyer buyer = currentBuyer(authentication);
        buyer.setName(req.name().trim());
        buyer.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        buyer.setDefaultAddress(req.defaultAddress() == null ? null : req.defaultAddress().toEntity());
        return ProfileResponse.from(buyerRepository.save(buyer));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication authentication) {
        Buyer buyer = currentBuyer(authentication);
        requireCurrentPassword(buyer, req.currentPassword());
        if (passwordEncoder.matches(req.newPassword(), buyer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new password must be different from the current one");
        }
        buyer.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        buyerRepository.save(buyer);
        return ResponseEntity.noContent().build();
    }

    /** After this, the old login token no longer matches an account, so the user must log in again. */
    @PostMapping("/email")
    public ProfileResponse changeEmail(@Valid @RequestBody ChangeEmailRequest req, Authentication authentication) {
        Buyer buyer = currentBuyer(authentication);
        requireCurrentPassword(buyer, req.currentPassword());
        String email = req.newEmail().trim().toLowerCase();
        if (email.equalsIgnoreCase(buyer.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is already your email");
        }
        // One email = one account, across buyers, sellers and the admin
        if (buyerRepository.existsByEmail(email) || sellerRepository.findByEmail(email).isPresent()
                || email.equalsIgnoreCase(adminEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }
        buyer.setEmail(email);
        return ProfileResponse.from(buyerRepository.save(buyer));
    }

    private Buyer currentBuyer(Authentication authentication) {
        return buyerRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only buyer accounts have a profile"));
    }

    private void requireCurrentPassword(Buyer buyer, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, buyer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
    }
}
