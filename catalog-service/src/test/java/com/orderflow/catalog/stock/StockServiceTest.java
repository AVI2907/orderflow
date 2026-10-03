package com.orderflow.catalog.stock;

import com.orderflow.catalog.product.Product;
import com.orderflow.catalog.stock.StockService.DeductRequest;
import com.orderflow.catalog.stock.StockService.Line;
import com.orderflow.catalog.stock.StockService.RestoreRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Stock must change exactly once per order or cancelled package, even if a request is repeated. */
class StockServiceTest {

    private final StockDeductionRepository deductions = mock(StockDeductionRepository.class);
    private final StockRestorationRepository restorations = mock(StockRestorationRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private StockService service;

    @BeforeEach
    void setUp() {
        service = new StockService(deductions, restorations);
        ReflectionTestUtils.setField(service, "em", em);
    }

    @Test
    void deductsStockForAPaidOrder() {
        Product mouse = product(10);

        var result = service.deduct(new DeductRequest(UUID.randomUUID(), List.of(new Line(mouse.getId(), 3))));

        assertThat(mouse.getStockQuantity()).isEqualTo(7);
        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(result.oversold()).isEmpty();
        verify(deductions).save(any());
    }

    @Test
    void repeatedDeductionForTheSameOrderIsIgnored() {
        UUID orderId = UUID.randomUUID();
        when(deductions.existsById(orderId)).thenReturn(true);

        var result = service.deduct(new DeductRequest(orderId, List.of(new Line(UUID.randomUUID(), 3))));

        assertThat(result.alreadyProcessed()).isTrue();
        verifyNoInteractions(em);
        verify(deductions, never()).save(any());
    }

    @Test
    void oversoldProductsStopAtZeroAndAreReported() {
        Product mouse = product(1);

        var result = service.deduct(new DeductRequest(UUID.randomUUID(), List.of(new Line(mouse.getId(), 3))));

        assertThat(mouse.getStockQuantity()).isZero();
        assertThat(result.oversold()).containsExactly(mouse.getId());
    }

    @Test
    void restoresStockForACancelledPackage() {
        Product lamp = product(7);

        var result = service.restore(new RestoreRequest(UUID.randomUUID(), List.of(new Line(lamp.getId(), 3))));

        assertThat(lamp.getStockQuantity()).isEqualTo(10);
        assertThat(result.alreadyProcessed()).isFalse();
        verify(restorations).save(any());
    }

    @Test
    void repeatedRestoreForTheSamePackageIsIgnored() {
        UUID packageId = UUID.randomUUID();
        when(restorations.existsById(packageId)).thenReturn(true);

        var result = service.restore(new RestoreRequest(packageId, List.of(new Line(UUID.randomUUID(), 3))));

        assertThat(result.alreadyProcessed()).isTrue();
        verifyNoInteractions(em);
    }

    private Product product(int stock) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setStockQuantity(stock);
        when(em.find(Product.class, p.getId(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(p);
        return p;
    }
}
