package com.cloudresilience.modules.health;

import com.cloudresilience.modules.events.messaging.EventTopology;
import com.cloudresilience.modules.events.persistence.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Low-cardinality business and event-processing metrics; identifiers never become labels. */
@Component
public class CloudResilienceMetrics {
    private static final Map<String, String> DLQ_QUEUES = Map.of(
            EventTopology.NOTIFICATION_CONSUMER, EventTopology.NOTIFICATION_DLQ,
            EventTopology.ACTIVITY_CONSUMER, EventTopology.ACTIVITY_DLQ);

    private final MeterRegistry registry;
    private final OutboxRepository outbox;
    private final AmqpAdmin broker;
    private final Map<String, AtomicLong> outboxCounts;
    private final AtomicLong oldestPendingSeconds = new AtomicLong();
    private final AtomicLong dlqNotifications = new AtomicLong();
    private final AtomicLong dlqActivity = new AtomicLong();
    private final AtomicLong snapshotAvailable = new AtomicLong(1);

    public CloudResilienceMetrics(MeterRegistry registry, OutboxRepository outbox, AmqpAdmin broker) {
        this.registry = registry;
        this.outbox = outbox;
        this.broker = broker;
        this.outboxCounts = Map.of(
                "PENDING", new AtomicLong(),
                "PROCESSING", new AtomicLong(),
                "PUBLISHED", new AtomicLong(),
                "FAILED", new AtomicLong());
        outboxCounts.forEach((status, value) -> registry.gauge("cloudresilience.outbox.events",
                io.micrometer.core.instrument.Tags.of("status", status), value));
        registry.gauge("cloudresilience.outbox.oldest.pending.seconds", oldestPendingSeconds);
        registry.gauge("cloudresilience.event.dlq.messages",
                io.micrometer.core.instrument.Tags.of("consumer", EventTopology.NOTIFICATION_CONSUMER), dlqNotifications);
        registry.gauge("cloudresilience.event.dlq.messages",
                io.micrometer.core.instrument.Tags.of("consumer", EventTopology.ACTIVITY_CONSUMER), dlqActivity);
        registry.gauge("cloudresilience.operational.snapshot.available", snapshotAvailable);
    }

    public void recordCheckout(boolean successful) {
        Counter.builder("cloudresilience.checkout.attempts")
                .tag("outcome", successful ? "success" : "failure").register(registry).increment();
    }

    public void recordOrderTransition(String targetStatus) {
        Runnable update = () -> Counter.builder("cloudresilience.order.transitions")
                .tag("to_status", targetStatus).register(registry).increment();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { update.run(); }
            });
        } else {
            update.run();
        }
    }

    public void recordPublished() {
        registry.counter("cloudresilience.outbox.events.published").increment();
    }

    public void recordPublishFailure(String category) {
        Counter.builder("cloudresilience.outbox.publish.failures")
                .tag("category", category).register(registry).increment();
    }

    public Timer.Sample startConsumerProcessing() { return Timer.start(registry); }

    public void finishConsumerProcessing(Timer.Sample sample, String consumer, boolean successful) {
        sample.stop(Timer.builder("cloudresilience.event.consumer.processing")
                .tag("consumer", consumer)
                .tag("outcome", successful ? "success" : "retry")
                .register(registry));
        if (!successful) {
            Counter.builder("cloudresilience.event.consumer.retries")
                    .tag("consumer", consumer).register(registry).increment();
        }
    }

    @Scheduled(fixedDelayString = "${app.observability.snapshot-interval-ms:15000}", initialDelay = 5000)
    public void refreshOperationalGauges() {
        try {
            Map<String, Long> counts = outbox.statusCounts();
            outboxCounts.forEach((status, value) -> value.set(counts.getOrDefault(status, 0L)));
            Long oldest = outbox.oldestPendingAgeSeconds();
            oldestPendingSeconds.set(oldest == null ? 0 : oldest);
            dlqNotifications.set(queueDepth(DLQ_QUEUES.get(EventTopology.NOTIFICATION_CONSUMER)));
            dlqActivity.set(queueDepth(DLQ_QUEUES.get(EventTopology.ACTIVITY_CONSUMER)));
            snapshotAvailable.set(1);
        } catch (RuntimeException unavailable) {
            snapshotAvailable.set(0);
        }
    }

    private long queueDepth(String name) {
        QueueInformation information = broker.getQueueInfo(name);
        return information == null ? 0 : information.getMessageCount();
    }
}
