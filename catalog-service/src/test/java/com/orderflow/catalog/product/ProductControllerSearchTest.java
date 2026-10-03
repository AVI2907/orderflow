package com.orderflow.catalog.product;

import com.orderflow.catalog.review.ReviewRepository;
import com.orderflow.catalog.seller.SellerRepository;
import com.orderflow.catalog.seller.SellerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Store search: user input is escaped and filters reach the database query correctly. */
class ProductControllerSearchTest {

    private final ProductSearchRepository search = mock(ProductSearchRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private ProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductController(mock(ProductRepository.class), mock(SellerRepository.class), reviews);
        ReflectionTestUtils.setField(controller, "searchRepository", search);
        when(reviews.summarizeByProduct()).thenReturn(List.of());
        when(search.search(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(List.of());
    }

    @Test
    void wildcardCharactersInSearchTextAreEscaped() {
        controller.list("50%_OFF", null, null, null, false, "newest");

        // % and _ are matched literally, and the search is case-insensitive
        verify(search).search(eq("%50\\%\\_off%"), eq(""), any(), any(), eq(0), eq(SellerStatus.APPROVED), any());
    }

    @Test
    void categoryAndInStockFiltersArePassedThrough() {
        controller.list(null, "  Electronics ", null, null, true, "newest");

        verify(search).search(eq("%%"), eq("electronics"), any(), any(), eq(1), eq(SellerStatus.APPROVED), any());
    }

    @Test
    void sortOptionsMapToDatabaseOrdering() {
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);

        controller.list(null, null, null, null, false, "price_asc");
        controller.list(null, null, null, null, false, "price_desc");
        controller.list(null, null, null, null, false, "anything-else");

        verify(search, times(3)).search(any(), any(), any(), any(), anyInt(), any(), sort.capture());
        assertThat(sort.getAllValues().get(0).getOrderFor("price").isAscending()).isTrue();
        assertThat(sort.getAllValues().get(1).getOrderFor("price").isDescending()).isTrue();
        assertThat(sort.getAllValues().get(2).getOrderFor("createdAt").isDescending()).isTrue();
    }
}
