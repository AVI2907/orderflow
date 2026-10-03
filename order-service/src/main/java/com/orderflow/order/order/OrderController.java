package com.orderflow.order.order;

import com.orderflow.order.catalog.CatalogClient;
import com.orderflow.order.catalog.CatalogClient.CatalogProduct;
import com.orderflow.order.config.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;

    public OrderController(OrderRepository orderRepository, CatalogClient catalogClient) {
        this.orderRepository = orderRepository;
        this.catalogClient = catalogClient;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest req, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user) || user.buyerId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only authenticated buyers can place orders");
        }

        // Combine repeated lines for the same product
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest line : req.items()) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }

        Order order = new Order();
        order.setBuyerId(UUID.fromString(user.buyerId()));
        order.setBuyerEmail(user.email());
        order.setShippingAddress(req.shippingAddress().toEntity());
        Map<UUID, SellerOrder> sellerOrders = new LinkedHashMap<>();

        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            // Price, name and seller come from catalog-service, never from the browser
            CatalogProduct product = catalogClient.getProduct(entry.getKey());
            int quantity = entry.getValue();

            if (!"APPROVED".equals(product.seller().status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, product.name() + " is no longer available");
            }
            if (product.stockQuantity() == null || product.stockQuantity() < quantity) {
                int left = product.stockQuantity() == null ? 0 : product.stockQuantity();
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        left == 0 ? product.name() + " is out of stock" : "Only " + left + " left of " + product.name());
            }

            // One sub-order per seller
            SellerOrder sellerOrder = sellerOrders.computeIfAbsent(product.seller().id(), sellerId -> {
                SellerOrder created = new SellerOrder();
                created.setOrder(order);
                created.setSellerId(sellerId);
                created.setSubtotal(BigDecimal.ZERO);
                order.getSellerOrders().add(created);
                return created;
            });

            OrderItem item = new OrderItem();
            item.setSellerOrder(sellerOrder);
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setUnitPrice(product.price());
            item.setQuantity(quantity);
            sellerOrder.getItems().add(item);
            sellerOrder.setSubtotal(sellerOrder.getSubtotal().add(product.price().multiply(BigDecimal.valueOf(quantity))));
        }

        order.setTotalAmount(order.getSellerOrders().stream()
                .map(SellerOrder::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

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
