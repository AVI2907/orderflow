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
    public record RestoreRequest(@NotNull UUID referenceId, @NotEmpty @Valid List<Line> items) {}
    public record RestoreResponse(boolean alreadyProcessed) {}

    @PersistenceContext
    private EntityManager em;

    private final StockDeductionRepository deductions;
    private final StockRestorationRepository restorations;

    public StockService(StockDeductionRepository deductions, StockRestorationRepository restorations) {
        this.deductions = deductions;
        this.restorations = restorations;
    }

    /** Deducts stock for a paid order exactly once. All lines succeed or none do. */
    @Transactional
    public DeductResponse deduct(DeductRequest req) {
        if (deductions.existsById(req.orderId())) {
            return new DeductResponse(true, List.of()); // repeat delivery: already done
        }

        List<UUID> oversold = new ArrayList<>();
        for (Line line : sortedForLocking(req.items())) {
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

    /** Puts stock back for a cancelled package exactly once. All lines succeed or none do. */
    @Transactional
    public RestoreResponse restore(RestoreRequest req) {
        if (restorations.existsById(req.referenceId())) {
            return new RestoreResponse(true); // repeat request: already done
        }

        for (Line line : sortedForLocking(req.items())) {
            Product product = em.find(Product.class, line.productId(), LockModeType.PESSIMISTIC_WRITE);
            if (product != null) { // a deleted product has nothing to restock
                product.setStockQuantity(product.getStockQuantity() + line.quantity());
            }
        }

        restorations.save(new StockRestoration(req.referenceId(), Instant.now()));
        return new RestoreResponse(false);
    }

    // Lock rows in a fixed order so two concurrent requests can't deadlock each other
    private static List<Line> sortedForLocking(List<Line> items) {
        List<Line> lines = new ArrayList<>(items);
        lines.sort(Comparator.comparing(Line::productId));
        return lines;
    }
}
