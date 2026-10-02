package com.orderflow.catalog.product;

import com.orderflow.catalog.seller.Seller;
import com.orderflow.catalog.seller.SellerRepository;
import com.orderflow.catalog.seller.SellerStatus;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Hands approved sellers a short-lived link to upload one product photo straight to S3. */
@RestController
@RequestMapping("/products/images")
public class ProductImageController {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    public record UploadUrlRequest(@NotBlank String contentType, @NotNull @Positive Long size) {}
    public record UploadUrlResponse(String uploadUrl, String imageUrl) {}

    private final String bucket;
    private final SellerRepository sellerRepository;
    private final S3Presigner presigner;

    public ProductImageController(@Value("${images.bucket:}") String bucket,
                                  @Value("${aws.region:us-east-1}") String region,
                                  SellerRepository sellerRepository) {
        this.bucket = bucket;
        this.sellerRepository = sellerRepository;
        this.presigner = S3Presigner.builder().region(Region.of(region)).build();
    }

    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl(@Valid @RequestBody UploadUrlRequest req, Authentication authentication) {
        Seller seller = sellerRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sellers can upload product photos"));
        if (seller.getStatus() != SellerStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your seller account has not been approved yet");
        }
        if (bucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Photo uploads are not configured");
        }
        String extension = EXTENSIONS.get(req.contentType());
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please use a JPEG, PNG or WebP image");
        }
        if (req.size() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Images must be 5 MB or smaller");
        }

        String key = "product-images/" + UUID.randomUUID() + "." + extension;
        // Type and size are part of the signature, so the upload must match exactly what was approved here
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(req.contentType())
                .contentLength(req.size())
                .build();
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(put)
                .build()).url().toString();

        return new UploadUrlResponse(url, "/" + key);
    }

    @PreDestroy
    void close() {
        presigner.close();
    }
}
