package com.orderflow.catalog.buyer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BuyerRepository extends JpaRepository<Buyer, UUID> {
    Optional<Buyer> findByEmail(String email);
    boolean existsByEmail(String email);
}
