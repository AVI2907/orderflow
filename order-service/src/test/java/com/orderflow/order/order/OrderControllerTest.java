package com.orderflow.order.order;

import com.orderflow.order.catalog.CatalogClient;
import com.orderflow.order.catalog.CatalogClient.CatalogProduct;
import com.orderflow.order.catalog.CatalogClient.CatalogSeller;
import com.orderflow.order.config.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Placing an order: prices and sellers must come from the catalog, never from the browser. */
class OrderControllerTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final OrderController controller = new OrderController(orderRepository, catalogClient);

    private final UUID buyerId = UUID.randomUUID();
    private final UUID sellerA = UUID.randomUUID();
    private final UUID sellerB = UUID.randomUUID();
    private final UUID mouseId = UUID.randomUUID();
    private final UUID lampId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void pricesComeFromTheCatalogAndTheOrderIsSplitPerSeller() {
        catalogHas(mouseId, "AWS Cloud Mouse", "29.99", 10, sellerA, "APPROVED");
        catalogHas(lampId, "LED Desk Lamp", "34.99", 5, sellerB, "APPROVED");

        OrderResponse response = controller.placeOrder(
                request(new OrderItemRequest(mouseId, 2), new OrderItemRequest(lampId, 1)), buyer()).getBody();

        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo("94.97");
        assertThat(response.sellerOrders()).hasSize(2);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getBuyerId()).isEqualTo(buyerId);
        assertThat(saved.getValue().getBuyerEmail()).isEqualTo("buyer@test.com");
    }

    @Test
    void repeatedLinesForTheSameProductAreCombined() {
        catalogHas(mouseId, "AWS Cloud Mouse", "29.99", 10, sellerA, "APPROVED");

        OrderResponse response = controller.placeOrder(
                request(new OrderItemRequest(mouseId, 1), new OrderItemRequest(mouseId, 2)), buyer()).getBody();

        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo("89.97");
        assertThat(response.sellerOrders()).hasSize(1);
        assertThat(response.sellerOrders().get(0).items()).hasSize(1);
        verify(catalogClient, times(1)).getProduct(mouseId);
    }

    @Test
    void rejectsQuantitiesAboveAvailableStock() {
        catalogHas(mouseId, "AWS Cloud Mouse", "29.99", 1, sellerA, "APPROVED");

        assertThatThrownBy(() -> controller.placeOrder(request(new OrderItemRequest(mouseId, 2)), buyer()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only 1 left")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsProductsFromSuspendedSellers() {
        catalogHas(mouseId, "AWS Cloud Mouse", "29.99", 10, sellerA, "SUSPENDED");

        assertThatThrownBy(() -> controller.placeOrder(request(new OrderItemRequest(mouseId, 1)), buyer()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void onlyBuyersCanPlaceOrders() {
        AuthenticatedUser seller = mock(AuthenticatedUser.class);
        when(seller.buyerId()).thenReturn(null);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(seller);

        assertThatThrownBy(() -> controller.placeOrder(request(new OrderItemRequest(mouseId, 1)), auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(catalogClient);
    }

    private Authentication buyer() {
        AuthenticatedUser user = mock(AuthenticatedUser.class);
        when(user.buyerId()).thenReturn(buyerId.toString());
        when(user.email()).thenReturn("buyer@test.com");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        return auth;
    }

    private PlaceOrderRequest request(OrderItemRequest... items) {
        ShippingAddressRequest address = mock(ShippingAddressRequest.class);
        when(address.toEntity()).thenReturn(new ShippingAddress());
        return new PlaceOrderRequest(List.of(items), address);
    }

    private void catalogHas(UUID id, String name, String price, int stock, UUID sellerId, String sellerStatus) {
        when(catalogClient.getProduct(id)).thenReturn(
                new CatalogProduct(id, name, new BigDecimal(price), stock, new CatalogSeller(sellerId, sellerStatus)));
    }
}
