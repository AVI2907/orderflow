package com.orderflow.order.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SellerOrderRepository extends JpaRepository<SellerOrder, UUID> {
    List<SellerOrder> findBySellerId(UUID sellerId);
}
