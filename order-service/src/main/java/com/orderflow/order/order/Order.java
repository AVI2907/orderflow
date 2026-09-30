package com.orderflow.order.order;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID buyerId;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Embedded
    private ShippingAddress shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus overallStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SellerOrder> sellerOrders = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (overallStatus == null) overallStatus = OrderStatus.PLACED;
    }

    public OrderStatus computeOverallStatus() {
        List<OrderStatus> statuses = sellerOrders.stream().map(SellerOrder::getStatus).toList();

        boolean allCancelled = statuses.stream().allMatch(s -> s == OrderStatus.CANCELLED);
        if (allCancelled) return OrderStatus.CANCELLED;

        return statuses.stream()
                .filter(s -> s != OrderStatus.CANCELLED)
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElse(OrderStatus.PLACED);
    }
}
