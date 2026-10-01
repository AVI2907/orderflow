package com.orderflow.catalog.stock;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One row per order whose stock has been deducted, so repeated requests are ignored. */
@Entity
@Table(name = "stock_deductions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeduction {

    @Id
    private UUID orderId;

    @Column(nullable = false)
    private Instant createdAt;
}
