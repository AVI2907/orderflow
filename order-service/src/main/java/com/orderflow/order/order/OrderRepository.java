package com.orderflow.order.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByBuyerId(UUID buyerId);

    /** The buyer's orders that have a delivery address, newest first (for pre-filling checkout). */
    @Query("select o from Order o where o.buyerId = :buyerId and o.shippingAddress.line1 is not null order by o.createdAt desc")
    List<Order> findWithAddressByBuyerId(@Param("buyerId") UUID buyerId);
}
