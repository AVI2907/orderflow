package com.orderflow.order.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingLinksTest {

    @Test
    void buildsCarrierLinks() {
        assertThat(TrackingLinks.url("UPS", "1Z999AA10123456784"))
                .isEqualTo("https://www.ups.com/track?tracknum=1Z999AA10123456784");
        assertThat(TrackingLinks.url("USPS", "9400100000000000000000"))
                .isEqualTo("https://tools.usps.com/go/TrackConfirmAction?tLabels=9400100000000000000000");
    }

    @Test
    void encodesTrackingNumbersSafely() {
        assertThat(TrackingLinks.url("FEDEX", "12 34")).isEqualTo("https://www.fedex.com/fedextrack/?trknbr=12+34");
    }

    @Test
    void noLinkForOtherCarriersOrMissingDetails() {
        assertThat(TrackingLinks.url("OTHER", "ABC123")).isNull();
        assertThat(TrackingLinks.url(null, "ABC123")).isNull();
        assertThat(TrackingLinks.url("UPS", null)).isNull();
    }
}
