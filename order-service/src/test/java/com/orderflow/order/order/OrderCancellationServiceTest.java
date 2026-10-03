package com.orderflow.order.order;

import com.orderflow.order.catalog.CatalogClient;
import com.orderflow.order.config.SqsEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cancelling packages: paid ones are restocked and refunded, shipped ones can't be cancelled. */
class OrderCancellationServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final StripePaymentService payments = mock(StripePaymentService.class);
    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final SqsEventPublisher events = mock(SqsEventPublisher.class);
    private final OrderCancellationService service =
            new OrderCancellationService(orderRepository, payments, catalogClient, events);

    @Test
    void paidPackageIsRestockedThenRefundedThenCancelled() {
        Order order = orderWith(OrderStatus.PAID, OrderStatus.PAID);
        SellerOrder first = order.getSellerOrders().get(0);
        SellerOrder second = order.getSellerOrders().get(1);
        when(payments.refundSellerOrder(order, first)).thenReturn("re_123");

        service.cancel(first, "BUYER");

        // Stock goes back before the money does, so a failed restock never leaves a refund without a cancellation
        InOrder inOrder = inOrder(catalogClient, payments);
        inOrder.verify(catalogClient).restoreStock(eq(first.getId()), anyList());
        inOrder.verify(payments).refundSellerOrder(order, first);

        assertThat(first.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(first.getRefundId()).isEqualTo("re_123");
        assertThat(first.getRefundedAmount()).isEqualByComparingTo("29.99");
        assertThat(first.getCancelledBy()).isEqualTo("BUYER");
        assertThat(first.getCancelledAt()).isNotNull();
        assertThat(second.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getOverallStatus()).isEqualTo(OrderStatus.PAID);
        verify(events).publish(any());
    }

    @Test
    void unpaidOrderIsCancelledEntirelyWithoutRefundOrRestock() {
        Order order = orderWith(OrderStatus.PLACED, OrderStatus.PLACED);

        service.cancel(order.getSellerOrders().get(0), "BUYER");

        assertThat(order.getSellerOrders()).allMatch(so -> so.getStatus() == OrderStatus.CANCELLED);
        assertThat(order.getOverallStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(payments, catalogClient);
    }

    @Test
    void sellersCannotCancelUnpaidOrders() {
        Order order = orderWith(OrderStatus.PLACED);

        assertThatThrownBy(() -> service.cancel(order.getSellerOrders().get(0), "SELLER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        assertThat(order.getSellerOrders().get(0).getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void shippedPackagesCannotBeCancelled() {
        Order order = orderWith(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> service.cancel(order.getSellerOrders().get(0), "BUYER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(payments, catalogClient);
    }

    @Test
    void cancellingTwiceDoesNothing() {
        Order order = orderWith(OrderStatus.CANCELLED);

        service.cancel(order.getSellerOrders().get(0), "BUYER");

        verifyNoInteractions(payments, catalogClient, events);
        verify(orderRepository, never()).save(any());
    }

    private Order orderWith(OrderStatus... statuses) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("29.99").multiply(BigDecimal.valueOf(statuses.length)));
        for (OrderStatus status : statuses) {
            SellerOrder so = new SellerOrder();
            so.setId(UUID.randomUUID());
            so.setOrder(order);
            so.setSellerId(UUID.randomUUID());
            so.setStatus(status);
            so.setSubtotal(new BigDecimal("29.99"));

            OrderItem item = new OrderItem();
            item.setSellerOrder(so);
            item.setProductId(UUID.randomUUID());
            item.setProductName("AWS Cloud Mouse");
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal("29.99"));
            so.getItems().add(item);

            order.getSellerOrders().add(so);
        }
        order.setOverallStatus(order.computeOverallStatus());
        return order;
    }
}
