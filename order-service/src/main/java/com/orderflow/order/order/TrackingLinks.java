package com.orderflow.order.order;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Supported carriers and their public tracking pages. */
final class TrackingLinks {

    static final Set<String> CARRIERS = Set.of("UPS", "USPS", "FEDEX", "DHL", "OTHER");

    private TrackingLinks() {}

    static String url(String carrier, String trackingNumber) {
        if (carrier == null || trackingNumber == null) return null;
        String n = URLEncoder.encode(trackingNumber, StandardCharsets.UTF_8);
        return switch (carrier) {
            case "UPS" -> "https://www.ups.com/track?tracknum=" + n;
            case "USPS" -> "https://tools.usps.com/go/TrackConfirmAction?tLabels=" + n;
            case "FEDEX" -> "https://www.fedex.com/fedextrack/?trknbr=" + n;
            case "DHL" -> "https://www.dhl.com/us-en/home/tracking/tracking-express.html?submit=1&tracking-id=" + n;
            default -> null; // OTHER: no known tracking page
        };
    }
}
