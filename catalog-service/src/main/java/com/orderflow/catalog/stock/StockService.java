package com.orderflow.catalog.stock;

import com.orderflow.catalog.product.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class StockService {

    public record Line(@NotNull UUID productId, @NotNull @Min(1) Integer quantity) {}
    public record DeductRequest(@NotNull UUID orderId, @NotEmpty @Valid List<Line> items) {}
    public record DeductResponse(boolean alreadyProcessed, List<UUID> oversold) {}

    @PersistenceContext
    private EntityManager em;

    private final StockDeductionRepository deductions;

    public StockService(StockDeductionRepository deductions) {
        this.deductions = deductions;
    }

    /** Deducts stock for a paid order exactly once. All lines succeed or none do. */
    @Transactional
    public DeductResponse deduct(DeductRequest req) {
        if (deductions.existsById(req.orderId())) {
            return new DeductResponse(true, List.of()); // repeat delivery: already done
        }

        // Lock rows in a fixed order so two concurrent orders can't deadlock each other
        List<Line> lines = new ArrayList<>(req.items());
        lines.sort(Comparator.comparing(Line::productId));

        List<UUID> oversold = new ArrayList<>();
        for (Line line : lines) {
            Product product = em.find(Product.class, line.productId(), LockModeType.PESSIMISTIC_WRITE);
            if (product == null) {
                oversold.add(line.productId()); // deleted since the order was placed
                continue;
            }
            int remaining = product.getStockQuantity() - line.quantity();
            if (remaining < 0) {
                oversold.add(product.getId());
                remaining = 0;
            }
            product.setStockQuantity(remaining);
        }

        deductions.save(new StockDeduction(req.orderId(), Instant.now()));
        return new DeductResponse(false, oversold);
    }
}
