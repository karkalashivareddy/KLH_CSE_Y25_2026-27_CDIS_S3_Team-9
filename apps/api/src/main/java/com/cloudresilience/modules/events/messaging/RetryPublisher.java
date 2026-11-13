package com.cloudresilience.modules.events.messaging;

import com.cloudresilience.modules.events.application.DeadLetterService;
import com.cloudresilience.modules.events.application.EventConsumptionService;
import com.cloudresilience.modules.events.application.EventEnvelopeCodec;
import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Bounded retry and dead-letter dispatch. A failed delivery is republished to a
 * TTL retry queue that dead-letters back to the original queue. After the last
 * attempt the message is quarantined, published to the durable DLQ and only
 * then acknowledged. If the retry publication itself fails the original message
 * is nacked for redelivery rather than acknowledged and lost.
 */
@Component
public class RetryPublisher {
    private static final Logger log = LoggerFactory.getLogger(RetryPublisher.class);
    private final RabbitTemplate rabbitTemplate;
    private final EventTopology topology;
    private final DeadLetterService deadLetters;
    private final EventEnvelopeCodec codec;

    public RetryPublisher(RabbitTemplate rabbitTemplate, EventTopology topology, DeadLetterService deadLetters,
            EventEnvelopeCodec codec) {
        this.rabbitTemplate = rabbitTemplate;
        this.topology = topology;
        this.deadLetters = deadLetters;
        this.codec = codec;
    }

    public void handleFailure(Message message, Channel channel, String consumerName, String consumerKey,
            RuntimeException failure) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        int attempts = retryCount(message);
        String category = category(failure);
        try {
            if (attempts >= EventTopology.MAX_RETRIES || nonRetryable(failure)) {
                EventEnvelope envelope = tryDecode(message);
                if (envelope != null) {
                    deadLetters.quarantine(envelope, routingKey(message), consumerName, category);
                } else {
                    deadLetters.quarantineRaw(message.getBody(), headerUuid(message, EventTopology.EVENT_ID_HEADER),
                            headerText(message, EventTopology.EVENT_TYPE_HEADER, "Unknown"), routingKey(message),
                            consumerName, category);
                }
                rabbitTemplate.send(EventTopology.DEAD_LETTER_EXCHANGE, "dead." + consumerKey, message);
                channel.basicAck(deliveryTag, false);
                log.warn("Event quarantined consumer={} attempts={} category={}", consumerName, attempts, category);
                return;
            }
            int nextAttempt = attempts + 1;
            Message retry = MessageBuilder.fromMessage(message)
                    .setHeader(EventTopology.RETRY_HEADER, nextAttempt)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbitTemplate.send(EventTopology.RETRY_EXCHANGE, topology.retryRoutingKey(consumerKey, nextAttempt), retry);
            channel.basicAck(deliveryTag, false);
            log.info("Event scheduled for retry consumer={} attempt={}", consumerName, nextAttempt);
        } catch (RuntimeException publishFailure) {
            log.error("Retry publication failed consumer={} category={}", consumerName, category);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private boolean nonRetryable(RuntimeException failure) {
        return failure instanceof EventEnvelopeCodec.InvalidEventException
                || failure instanceof EventConsumptionService.UnsupportedEventVersionException
                || failure instanceof PoisonEventException;
    }

    private String category(RuntimeException failure) {
        if (failure instanceof EventEnvelopeCodec.InvalidEventException) return "INVALID_EVENT";
        if (failure instanceof EventConsumptionService.UnsupportedEventVersionException) return "UNSUPPORTED_VERSION";
        if (failure instanceof PoisonEventException) return "POISON_EVENT";
        if (failure instanceof IllegalArgumentException) return "INVALID_PAYLOAD";
        return "CONSUMER_ERROR";
    }

    private String routingKey(Message message) {
        String key = message.getMessageProperties().getReceivedRoutingKey();
        return key == null || key.isBlank() ? "unknown" : key;
    }

    private int retryCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get(EventTopology.RETRY_HEADER);
        if (header instanceof Number number) return number.intValue();
        if (header instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private EventEnvelope tryDecode(Message message) {
        try {
            return codec.decode(message.getBody());
        } catch (RuntimeException undecodable) {
            return null;
        }
    }

    private UUID headerUuid(Message message, String header) {
        Object value = message.getMessageProperties().getHeaders().get(header);
        if (value != null) {
            try {
                return UUID.fromString(value.toString());
            } catch (IllegalArgumentException ignored) {
                // fall through to a generated identity
            }
        }
        return UUID.randomUUID();
    }

    private String headerText(Message message, String header, String fallback) {
        Object value = message.getMessageProperties().getHeaders().get(header);
        return value == null ? fallback : value.toString();
    }

    public static class PoisonEventException extends RuntimeException {
        public PoisonEventException(String message) { super(message); }
    }
}
