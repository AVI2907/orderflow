package com.orderflow.catalog.stock;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Service-to-service only: not routed by CloudFront, and protected by a shared secret. */
@RestController
@RequestMapping("/internal/stock")
public class StockController {

    private final byte[] apiKey;
    private final StockService stockService;

    public StockController(@Value("${internal.api-key:}") String apiKey, StockService stockService) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.stockService = stockService;
    }

    @PostMapping("/deduct")
    public StockService.DeductResponse deduct(
            @RequestHeader(value = "X-Internal-Key", required = false) String providedKey,
            @Valid @RequestBody StockService.DeductRequest req) {
        // Constant-time comparison, so the key can't be guessed from response timing
        if (apiKey.length == 0 || providedKey == null
                || !MessageDigest.isEqual(apiKey, providedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return stockService.deduct(req);
    }
}
