package com.orderflow.catalog.buyer;

import com.orderflow.catalog.buyer.BuyerProfileController.*;
import com.orderflow.catalog.seller.Seller;
import com.orderflow.catalog.seller.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Profile changes: only your own account, and sensitive changes need your current password. */
class BuyerProfileControllerTest {

    private final BuyerRepository buyers = mock(BuyerRepository.class);
    private final SellerRepository sellers = mock(SellerRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final BuyerProfileController controller =
            new BuyerProfileController(buyers, sellers, encoder, "admin@orderflow.com");

    private Buyer buyer;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        buyer = new Buyer();
        buyer.setId(UUID.randomUUID());
        buyer.setEmail("buyer@test.com");
        buyer.setName("Old Name");
        buyer.setPasswordHash("HASH");
        when(buyers.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
        when(buyers.save(any(Buyer.class))).thenAnswer(inv -> inv.getArgument(0));
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("buyer@test.com");
    }

    @Test
    void updatesNamePhoneAndDefaultAddress() {
        controller.update(new UpdateProfileRequest("  New Name ", "+1 215 555 0100",
                new AddressDto("New Name", "1 Main St", " ", "Philadelphia", "PA", "19104", "United States")), auth);

        assertThat(buyer.getName()).isEqualTo("New Name");
        assertThat(buyer.getPhone()).isEqualTo("+1 215 555 0100");
        assertThat(buyer.getDefaultAddress().getCity()).isEqualTo("Philadelphia");
        assertThat(buyer.getDefaultAddress().getLine2()).isNull();
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        when(encoder.matches("wrong", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> controller.changePassword(new ChangePasswordRequest("wrong", "newpassword1"), auth))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Current password is incorrect");
        verify(buyers, never()).save(any());
    }

    @Test
    void passwordChangeStoresTheNewHash() {
        when(encoder.matches("right", "HASH")).thenReturn(true);
        when(encoder.matches("newpassword1", "HASH")).thenReturn(false);
        when(encoder.encode("newpassword1")).thenReturn("NEW_HASH");

        controller.changePassword(new ChangePasswordRequest("right", "newpassword1"), auth);

        assertThat(buyer.getPasswordHash()).isEqualTo("NEW_HASH");
    }

    @Test
    void emailAlreadyUsedBySellerIsRejected() {
        when(encoder.matches("right", "HASH")).thenReturn(true);
        when(sellers.findByEmail("shop@test.com")).thenReturn(Optional.of(new Seller()));

        assertThatThrownBy(() -> controller.changeEmail(new ChangeEmailRequest("right", "Shop@Test.com"), auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        assertThat(buyer.getEmail()).isEqualTo("buyer@test.com");
    }

    @Test
    void adminEmailCannotBeTaken() {
        when(encoder.matches("right", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> controller.changeEmail(new ChangeEmailRequest("right", "admin@orderflow.com"), auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void emailChangeIsSavedLowercase() {
        when(encoder.matches("right", "HASH")).thenReturn(true);

        controller.changeEmail(new ChangeEmailRequest("right", "New@Example.com"), auth);

        assertThat(buyer.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void nonBuyerAccountsHaveNoProfile() {
        Authentication seller = mock(Authentication.class);
        when(seller.getName()).thenReturn("seller@test.com");
        when(buyers.findByEmail("seller@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(seller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }
}
