package com.orderflow.order.config;

import com.orderflow.order.order.OrderEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Component
public class SqsEventPublisher {

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsEventPublisher(SqsClient sqsClient, @Value("${aws.sqs.queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    public void publish(OrderEvent event) {
        try {
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(toJson(event))
                    .build()).messageId();
            // Log IDs only: the body contains the buyer's email address
            System.out.println("Published " + event.newStatus() + " event for package " + event.sellerOrderId()
                    + ", messageId=" + messageId);
        } catch (Exception e) {
            // A messaging failure must not break the order itself
            System.err.println("Failed to publish event to SQS: " + e);
        }
    }

    private static String toJson(OrderEvent e) {
        String items = e.items().stream()
                .map(i -> "{\"name\":" + str(i.name()) + ",\"quantity\":" + i.quantity()
                        + ",\"unitPrice\":" + num(i.unitPrice()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{"
                + "\"orderId\":" + obj(e.orderId())
                + ",\"sellerOrderId\":" + obj(e.sellerOrderId())
                + ",\"sellerId\":" + obj(e.sellerId())
                + ",\"oldStatus\":" + obj(e.oldStatus())
                + ",\"newStatus\":" + obj(e.newStatus())
                + ",\"timestamp\":" + obj(e.timestamp())
                + ",\"buyerEmail\":" + str(e.buyerEmail())
                + ",\"buyerName\":" + str(e.buyerName())
                + ",\"orderTotal\":" + num(e.orderTotal())
                + ",\"packageSubtotal\":" + num(e.packageSubtotal())
                + ",\"items\":" + items
                + ",\"carrier\":" + str(e.carrier())
                + ",\"trackingNumber\":" + str(e.trackingNumber())
                + ",\"trackingUrl\":" + str(e.trackingUrl())
                + ",\"refundedAmount\":" + num(e.refundedAmount())
                + "}";
    }

    private static String obj(Object o) {
        return o == null ? "null" : str(o.toString());
    }

    private static String num(BigDecimal n) {
        return n == null ? "null" : n.toPlainString();
    }

    /** JSON string with proper escaping, so names like 27" 4K Monitor don't break the message. */
    private static String str(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
