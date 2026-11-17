package com.cloudresilience.modules.events.messaging;

import com.cloudresilience.modules.events.application.EventConsumptionService;
import com.cloudresilience.modules.events.application.EventEnvelopeCodec;
import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.health.CloudResilienceMetrics;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.function.Consumer;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

/**
 * Shared listener behavior: decode, run the idempotent transactional consumer,
 * then acknowledge. Acknowledgement happens only after the durable work commits.
 */
@Component
public class EventMessageHandler {
    private final EventEnvelopeCodec codec;
    private final EventConsumptionService consumption;
    private final RetryPublisher retries;
    private final CloudResilienceMetrics metrics;

    public EventMessageHandler(EventEnvelopeCodec codec, EventConsumptionService consumption, RetryPublisher retries,
            CloudResilienceMetrics metrics) {
        this.codec = codec;
        this.consumption = consumption;
        this.retries = retries;
        this.metrics = metrics;
    }

    public void handle(Message message, Channel channel, String consumerName, String consumerKey,
            Consumer<EventEnvelope> effect) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        var sample = metrics.startConsumerProcessing();
        boolean successful = false;
        try {
            EventEnvelope envelope = codec.decode(message.getBody());
            consumption.consume(envelope, consumerName, () -> effect.accept(envelope));
            channel.basicAck(deliveryTag, false);
            successful = true;
        } catch (RuntimeException failure) {
            retries.handleFailure(message, channel, consumerName, consumerKey, failure);
        } finally {
            metrics.finishConsumerProcessing(sample, consumerName, successful);
        }
    }
}
