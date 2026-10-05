package com.orderflow.order.order;

import com.orderflow.order.config.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Pre-fills checkout with the logged-in buyer's most recent delivery address. */
@RestController
public class SavedAddressController {

    private final OrderRepository orderRepository;

    public SavedAddressController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 200 with the address, or 204 if this buyer has never ordered with an address. */
    @GetMapping("/orders/last-address")
    public ResponseEntity<ShippingAddress> lastAddress(Authentication authentication) {
        // The buyer always comes from the login token, so nobody can read someone else's address
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user) || user.buyerId() == null) {
            return ResponseEntity.noContent().build();
        }
        return orderRepository.findWithAddressByBuyerId(UUID.fromString(user.buyerId())).stream()
            .findFirst()
            .map(Order::getShippingAddress)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }
}
