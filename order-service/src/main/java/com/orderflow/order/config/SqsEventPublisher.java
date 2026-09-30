package com.orderflow.order.config;

import com.orderflow.order.order.OrderEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Component
public class SqsEventPublisher {

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsEventPublisher(
            SqsClient sqsClient,
            @Value("${aws.sqs.queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    public void publish(OrderEvent event) {
        try {
            String body = toJson(event);
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
            System.out.println("Published SQS event, messageId=" + response.messageId() + " body=" + body);
        } catch (Exception e) {
            // Don't let a messaging failure break the actual business transaction.
            // In production this would go to a dead-letter/retry mechanism instead of just logging.
            System.err.println("Failed to publish event to SQS: " + e);
        }
    }

    private String toJson(OrderEvent event) {
        return "{"
                + "\"orderId\":\"" + event.orderId() + "\","
                + "\"sellerOrderId\":\"" + event.sellerOrderId() + "\","
                + "\"sellerId\":\"" + event.sellerId() + "\","
                + "\"oldStatus\":\"" + event.oldStatus() + "\","
                + "\"newStatus\":\"" + event.newStatus() + "\","
                + "\"timestamp\":\"" + event.timestamp() + "\""
                + "}";
    }
}
