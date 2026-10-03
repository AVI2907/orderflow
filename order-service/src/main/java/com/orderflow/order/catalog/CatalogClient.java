package com.orderflow.order.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Talks to catalog-service: authoritative product data, and stock changes after payment or cancellation. */
@Component
public class CatalogClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogSeller(UUID id, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogProduct(UUID id, String name, BigDecimal price, Integer stockQuantity, CatalogSeller seller) {}

    public record StockLine(UUID productId, int quantity) {}
    public record DeductRequest(UUID orderId, List<StockLine> items) {}
    public record RestoreRequest(UUID referenceId, List<StockLine> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeductResponse(boolean alreadyProcessed, List<UUID> oversold) {}

    private final RestClient restClient;
    private final String internalApiKey;

    public CatalogClient(@Value("${catalog.base-url}") String baseUrl,
                         @Value("${internal.api-key:}") String internalApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalApiKey = internalApiKey;
    }

    public CatalogProduct getProduct(UUID productId) {
        try {
            CatalogProduct product = restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(CatalogProduct.class);
            if (product == null || product.price() == null || product.seller() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catalog returned incomplete product data");
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product in your cart is no longer available");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not verify product prices right now. Please try again.", e);
        }
    }

    /**
     * Reduces stock for a paid order. Safe to call more than once for the same order.
     * Throws on failure, so the caller's transaction rolls back and the webhook is retried.
     * Returns the products that didn't have enough stock left.
     */
    public List<UUID> deductStock(UUID orderId, List<StockLine> items) {
        requireKey();
        DeductResponse response = restClient.post()
                .uri("/internal/stock/deduct")
                .header("X-Internal-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DeductRequest(orderId, items))
                .retrieve()
                .body(DeductResponse.class);
        return response == null || response.oversold() == null ? List.of() : response.oversold();
    }

    /** Puts stock back for a cancelled package. Safe to call more than once with the same referenceId. */
    public void restoreStock(UUID referenceId, List<StockLine> items) {
        requireKey();
        try {
            restClient.post()
                    .uri("/internal/stock/restore")
                    .header("X-Internal-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RestoreRequest(referenceId, items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not update stock right now. Please try again.", e);
        }
    }

    private void requireKey() {
        if (internalApiKey.isBlank()) {
            throw new IllegalStateException("internal.api-key is not configured");
        }
    }
}
