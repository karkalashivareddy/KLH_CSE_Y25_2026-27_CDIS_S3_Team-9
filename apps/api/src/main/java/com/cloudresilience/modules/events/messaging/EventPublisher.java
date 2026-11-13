package com.cloudresilience.modules.events.messaging;

import com.cloudresilience.modules.events.persistence.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes a persistent message and waits for the broker's publisher confirm.
 * An unroutable mandatory message is treated as a failure, so it is retried
 * instead of being silently dropped.
 */
@Component
public class EventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public EventPublisher(RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = Duration.ofMillis(confirmTimeoutMs);
    }

    public void publish(OutboxEvent event, String routingKey) {
        publish(event.payload().getBytes(StandardCharsets.UTF_8), event.id(), event.eventType(),
                event.eventVersion(), event.correlationId(), routingKey);
    }

    public void publish(byte[] body, UUID eventId, String eventType, int eventVersion, UUID correlationId,
            String routingKey) {
        CorrelationData correlation = new CorrelationData(eventId.toString());
        rabbitTemplate.convertAndSend(EventTopology.EVENTS_EXCHANGE, routingKey, body, message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(eventId.toString());
            properties.setHeader(EventTopology.EVENT_ID_HEADER, eventId.toString());
            properties.setHeader(EventTopology.EVENT_TYPE_HEADER, eventType);
            properties.setHeader(EventTopology.EVENT_VERSION_HEADER, eventVersion);
            if (correlationId != null) properties.setHeader(EventTopology.CORRELATION_ID_HEADER, correlationId.toString());
            return message;
        }, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) throw new EventPublishException("Broker rejected the event (nack)");
            if (correlation.getReturned() != null) throw new EventPublishException("Event was not routable");
        } catch (EventPublishException failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrupted while awaiting publisher confirm");
        } catch (Exception failure) {
            throw new EventPublishException("Publisher confirm failed", failure);
        }
    }

    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message) { super(message); }
        public EventPublishException(String message, Throwable cause) { super(message, cause); }
    }
}
