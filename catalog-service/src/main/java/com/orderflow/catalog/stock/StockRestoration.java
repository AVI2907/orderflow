package com.orderflow.catalog.stock;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One row per cancelled package whose stock has been put back, so repeated requests are ignored. */
@Entity
@Table(name = "stock_restorations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRestoration {

    @Id
    private UUID referenceId;

    @Column(nullable = false)
    private Instant createdAt;
}
