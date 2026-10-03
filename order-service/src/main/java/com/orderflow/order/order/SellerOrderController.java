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

    @org.springframework.beans.factory.annotation.Autowired
    private OrderCancellationService cancellationService;

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

        // Only Stripe's verified webhook may mark an order as paid
        if (req.status() == OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Orders are marked paid automatically when payment succeeds");
        }
        // Cancelling goes through the cancellation service, which restocks and refunds paid packages
        if (req.status() == OrderStatus.CANCELLED) {
            cancellationService.cancel(sellerOrder, isAdmin ? "ADMIN" : "SELLER");
            return ResponseEntity.ok(SellerOrderResponse.from(sellerOrder));
        }

        if (req.status() == OrderStatus.SHIPPED) {
            String carrier = req.carrier() == null ? "" : req.carrier().trim().toUpperCase();
            String tracking = req.trackingNumber() == null ? "" : req.trackingNumber().trim();
            if (!TrackingLinks.CARRIERS.contains(carrier)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choose a carrier: UPS, USPS, FedEx, DHL or Other");
            }
            if (!tracking.matches("[A-Za-z0-9 -]{4,64}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid tracking number");
            }
            sellerOrder.setCarrier(carrier);
            sellerOrder.setTrackingNumber(tracking);
            sellerOrder.setShippedAt(Instant.now());
        } else if (req.status() == OrderStatus.DELIVERED) {
            sellerOrder.setDeliveredAt(Instant.now());
        }

        sellerOrder.setStatus(req.status());
        SellerOrder savedSellerOrder = sellerOrderRepository.save(sellerOrder);

        Order order = savedSellerOrder.getOrder();
        order.setOverallStatus(order.computeOverallStatus());
        orderRepository.save(order);

        eventPublisher.publish(OrderEvent.of(order, savedSellerOrder, oldStatus, req.status()));

        return ResponseEntity.ok(SellerOrderResponse.from(savedSellerOrder));
    }

    /** Cancel one package before it ships. Allowed for the buyer, the package's seller, or an admin. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SellerOrderResponse> cancel(@PathVariable UUID id, Authentication authentication) {
        SellerOrder sellerOrder = sellerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        AuthenticatedUser user = authentication.getPrincipal() instanceof AuthenticatedUser u ? u : null;
        boolean isSeller = user != null && user.sellerId() != null
                && user.sellerId().equals(sellerOrder.getSellerId().toString());
        boolean isBuyer = user != null && user.buyerId() != null
                && user.buyerId().equals(sellerOrder.getOrder().getBuyerId().toString());
        if (!isAdmin && !isSeller && !isBuyer) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        cancellationService.cancel(sellerOrder, isAdmin ? "ADMIN" : isSeller ? "SELLER" : "BUYER");
        return ResponseEntity.ok(SellerOrderResponse.from(sellerOrder));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<SellerOrderResponse>> listBySeller(@PathVariable UUID sellerId, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        boolean isThatSeller = authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.sellerId() != null
                && user.sellerId().equals(sellerId.toString());
        if (!isAdmin && !isThatSeller) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(
                sellerOrderRepository.findBySellerId(sellerId).stream().map(SellerOrderResponse::from).toList());
    }
}
