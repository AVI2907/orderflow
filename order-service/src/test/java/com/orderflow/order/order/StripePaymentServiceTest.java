package com.orderflow.order.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StripePaymentServiceTest {

    @Test
    void convertsDollarsToWholeCents() {
        assertThat(StripePaymentService.toCents(new BigDecimal("29.99"))).isEqualTo(2999);
        assertThat(StripePaymentService.toCents(new BigDecimal("10"))).isEqualTo(1000);
        assertThat(StripePaymentService.toCents(new BigDecimal("94.97"))).isEqualTo(9497);
    }

    @Test
    void roundsHalfCentsUp() {
        assertThat(StripePaymentService.toCents(new BigDecimal("0.005"))).isEqualTo(1);
    }
}
