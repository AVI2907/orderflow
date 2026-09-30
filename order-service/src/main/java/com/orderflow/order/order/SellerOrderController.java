package com.orderflow.order.order;

import com.orderflow.order.config.AuthenticatedUser;
import com.orderflow.order.config.SqsEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/seller-orders")
public class SellerOrderController {

    private final SellerOrderRepository sellerOrderRepository;
    private final OrderRepository orderRepository;
    private final SqsEventPublisher eventPublisher;

    public SellerOrderController(
            SellerOrderRepository sellerOrderRepository,
            OrderRepository orderRepository,
            SqsEventPublisher eventPublisher) {
        this.sellerOrderRepository = sellerOrderRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<SellerOrderResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SellerOrderStatusUpdateRequest req,
            Authentication authentication) {

        SellerOrder sellerOrder = sellerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seller order not found: " + id));

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
            String tokenSellerId = user.sellerId();

            if (tokenSellerId == null || !tokenSellerId.equals(sellerOrder.getSellerId().toString())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this seller order");
            }
        }

        OrderStatus oldStatus = sellerOrder.getStatus();

        if (!oldStatus.canTransitionTo(req.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot transition from " + oldStatus + " to " + req.status());
        }

        sellerOrder.setStatus(req.status());
        SellerOrder savedSellerOrder = sellerOrderRepository.save(sellerOrder);

        Order order = savedSellerOrder.getOrder();
        order.setOverallStatus(order.computeOverallStatus());
        orderRepository.save(order);

        eventPublisher.publish(new OrderEvent(
                order.getId(), savedSellerOrder.getId(), savedSellerOrder.getSellerId(),
                oldStatus, req.status(), Instant.now()));

        return ResponseEntity.ok(SellerOrderResponse.from(savedSellerOrder));
    }

    @GetMapping("/seller/{sellerId}")
    public List<SellerOrderResponse> listBySeller(@PathVariable UUID sellerId) {
        return sellerOrderRepository.findBySellerId(sellerId).stream().map(SellerOrderResponse::from).toList();
    }
}
