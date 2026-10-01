package com.orderflow.catalog.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockDeductionRepository extends JpaRepository<StockDeduction, UUID> {}
