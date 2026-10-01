package com.orderflow.order.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/** Looks up authoritative product data (price, name, seller, stock) from catalog-service. */
@Component
public class CatalogClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogSeller(UUID id, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogProduct(UUID id, String name, BigDecimal price, Integer stockQuantity, CatalogSeller seller) {}

    private final RestClient restClient;

    public CatalogClient(@Value("${catalog.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
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
}
