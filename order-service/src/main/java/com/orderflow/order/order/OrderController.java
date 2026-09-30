package com.orderflow.order.order;

import com.orderflow.order.config.AuthenticatedUser;
import com.orderflow.order.config.SqsEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final SqsEventPublisher eventPublisher;

    public OrderController(OrderRepository orderRepository, SqsEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest req, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user) || user.buyerId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only authenticated buyers can place orders");
        }
        UUID buyerId = UUID.fromString(user.buyerId());

        Order order = new Order();
        order.setBuyerId(buyerId);
        order.setShippingAddress(req.shippingAddress().toEntity());

        BigDecimal orderTotal = BigDecimal.ZERO;

        for (SellerOrderRequest soReq : req.sellerOrders()) {
            SellerOrder sellerOrder = new SellerOrder();
            sellerOrder.setOrder(order);
            sellerOrder.setSellerId(soReq.sellerId());

            BigDecimal subtotal = BigDecimal.ZERO;

            for (OrderItemRequest itemReq : soReq.items()) {
                OrderItem item = new OrderItem();
                item.setSellerOrder(sellerOrder);
                item.setProductId(itemReq.productId());
                item.setProductName(itemReq.productName());
                item.setUnitPrice(itemReq.unitPrice());
                item.setQuantity(itemReq.quantity());

                subtotal = subtotal.add(itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity())));
                sellerOrder.getItems().add(item);
            }

            sellerOrder.setSubtotal(subtotal);
            orderTotal = orderTotal.add(subtotal);
            order.getSellerOrders().add(sellerOrder);
        }

        order.setTotalAmount(orderTotal);

        Order saved = orderRepository.save(order);
        return ResponseEntity.ok(OrderResponse.from(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id, Authentication authentication) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null || authentication == null) {
            return ResponseEntity.notFound().build();
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOwner = authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.buyerId() != null
                && user.buyerId().equals(order.getBuyerId().toString());
        // 404 rather than 403, so other people's order IDs can't even be confirmed to exist
        if (!isAdmin && !isOwner) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<List<OrderResponse>> listByBuyer(@PathVariable UUID buyerId, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            if (!(authentication.getPrincipal() instanceof AuthenticatedUser user)
                    || user.buyerId() == null
                    || !user.buyerId().equals(buyerId.toString())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        List<OrderResponse> orders = orderRepository.findByBuyerId(buyerId).stream().map(OrderResponse::from).toList();
        return ResponseEntity.ok(orders);
    }
}
