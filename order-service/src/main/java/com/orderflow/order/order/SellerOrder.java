package com.orderflow.order.order;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "seller_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "sellerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Shipping details, filled in when the seller marks this part of the order as shipped
    @Column(length = 20)
    private String carrier;

    @Column(length = 64)
    private String trackingNumber;

    // When each step happened (empty for steps not reached yet, and for orders older than this feature)
    private Instant paidAt;
    private Instant shippedAt;
    private Instant deliveredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) status = OrderStatus.PLACED;
    }
}
