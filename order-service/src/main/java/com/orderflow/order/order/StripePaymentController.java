package com.orderflow.order.order;

import com.orderflow.order.config.AuthenticatedUser;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class StripePaymentController {

    private final StripePaymentService paymentService;
    private final String webhookSecret;

    public StripePaymentController(
            StripePaymentService paymentService,
            @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    public record PaymentIntentResponse(String clientSecret) {}

    @PostMapping("/orders/{id}/payment-intent")
    public PaymentIntentResponse createPaymentIntent(@PathVariable UUID id, Authentication authentication) {
        String buyerId = authentication.getPrincipal() instanceof AuthenticatedUser user ? user.buyerId() : null;
        return new PaymentIntentResponse(paymentService.createPaymentIntent(id, buyerId));
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (signature == null) {
            return ResponseEntity.badRequest().build();
        }

        final Event event;
        try {
            // Proves the request really came from Stripe and wasn't tampered with
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        if ("payment_intent.succeeded".equals(event.getType())) {
            StripeObject object = event.getDataObjectDeserializer().getObject().orElseGet(() -> {
                try {
                    // Falls back if the webhook's API version differs from the library's
                    return event.getDataObjectDeserializer().deserializeUnsafe();
                } catch (Exception e) {
                    return null;
                }
            });

            if (object instanceof PaymentIntent intent) {
                String orderId = intent.getMetadata().get("orderId");
                Long received = intent.getAmountReceived();
                if (orderId != null && received != null) {
                    paymentService.markPaid(UUID.fromString(orderId), received, intent.getId());
                }
            }
        }

        // Always acknowledge verified events so Stripe doesn't keep retrying ones we ignore
        return ResponseEntity.ok().build();
    }
}
