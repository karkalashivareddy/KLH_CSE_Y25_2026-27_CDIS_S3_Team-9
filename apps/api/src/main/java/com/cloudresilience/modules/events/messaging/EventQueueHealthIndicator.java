package com.cloudresilience.modules.events.messaging;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports broker queue depth for consumer queues, retry queues and DLQs. */
@Component("eventQueues")
public class EventQueueHealthIndicator implements HealthIndicator {
    private final AmqpAdmin amqpAdmin;
    private final EventTopology topology;

    public EventQueueHealthIndicator(AmqpAdmin amqpAdmin, EventTopology topology) {
        this.amqpAdmin = amqpAdmin;
        this.topology = topology;
    }

    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();
            builder.withDetail(EventTopology.NOTIFICATION_QUEUE, depth(EventTopology.NOTIFICATION_QUEUE));
            builder.withDetail(EventTopology.ACTIVITY_QUEUE, depth(EventTopology.ACTIVITY_QUEUE));
            builder.withDetail("deadLetters", depth(EventTopology.NOTIFICATION_DLQ) + depth(EventTopology.ACTIVITY_DLQ));
            return builder.build();
        } catch (RuntimeException brokerUnavailable) {
            return Health.down().withDetail("error", "broker unavailable").build();
        }
    }

    private long depth(String queue) {
        QueueInformation information = amqpAdmin.getQueueInfo(queue);
        return information == null ? 0 : information.getMessageCount();
    }
}
