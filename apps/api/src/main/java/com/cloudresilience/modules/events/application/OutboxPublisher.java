package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventType;
import com.cloudresilience.modules.events.messaging.EventPublisher;
import com.cloudresilience.modules.events.persistence.OutboxEvent;
import com.cloudresilience.modules.events.persistence.OutboxRepository;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Two-step claim/publish/finalize publisher. The claim and the finalize are
 * short database statements; the broker call happens outside any database
 * transaction. Delivery is at-least-once, so duplicate publication is expected
 * and consumers deduplicate by event id.
 */
@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final EventPublisher publisher;
    private final boolean enabled;
    private final int batchSize;
    private final int leaseSeconds;
    private final int maxAttempts;
    private final int baseBackoffSeconds;
    private final int maxBackoffSeconds;
    private final String owner;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxPublisher(OutboxRepository repository, EventPublisher publisher,
            @Value("${app.outbox.publisher.enabled:true}") boolean enabled,
            @Value("${app.outbox.publisher.batch-size:50}") int batchSize,
            @Value("${app.outbox.publisher.lease-seconds:30}") int leaseSeconds,
            @Value("${app.outbox.publisher.max-attempts:8}") int maxAttempts,
            @Value("${app.outbox.publisher.base-backoff-seconds:2}") int baseBackoffSeconds,
            @Value("${app.outbox.publisher.max-backoff-seconds:300}") int maxBackoffSeconds) {
        this.repository = repository;
        this.publisher = publisher;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
        this.owner = instanceOwner();
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.interval-ms:1000}",
            initialDelayString = "${app.outbox.publisher.initial-delay-ms:2000}")
    public void publishDue() {
        publishDueOnce();
    }

    /** Runs one bounded publishing pass. Exposed for deterministic tests and operations. */
    public int publishDueOnce() {
        if (!enabled) return 0;
        if (!running.compareAndSet(false, true)) return 0;
        try {
            List<OutboxEvent> claimed = repository.claimDue(batchSize, owner, leaseSeconds);
            int published = 0;
            for (OutboxEvent event : claimed) {
                try {
                    publisher.publish(event, routingKey(event.eventType()));
                    repository.markPublished(event.id(), owner);
                    published++;
                } catch (RuntimeException failure) {
                    int nextAttempt = event.attemptCount() + 1;
                    int backoff = backoffSeconds(nextAttempt);
                    repository.markFailed(event.id(), owner, category(failure), backoff, maxAttempts);
                    log.warn("Outbox publish failed eventId={} eventType={} attempt={} category={}",
                            event.id(), event.eventType(), nextAttempt, category(failure));
                }
            }
            return published;
        } finally {
            running.set(false);
        }
    }

    private String routingKey(String eventType) {
        return EventType.fromWireName(eventType).routingKey();
    }

    private int backoffSeconds(int attempt) {
        long exponential = (long) baseBackoffSeconds << Math.min(Math.max(attempt - 1, 0), 20);
        return (int) Math.min(exponential, maxBackoffSeconds);
    }

    private String category(RuntimeException failure) {
        if (failure instanceof EventPublisher.EventPublishException publish) {
            String message = publish.getMessage() == null ? "" : publish.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (message.contains("routable")) return "UNROUTABLE";
            if (message.contains("rejected")) return "BROKER_NACK";
            if (message.contains("confirm")) return "CONFIRM_TIMEOUT";
            return "PUBLISH_ERROR";
        }
        return "BROKER_UNAVAILABLE";
    }

    public String owner() { return owner; }

    private static String instanceOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            host = "unknown-host";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName().replaceAll("[^A-Za-z0-9._-]", "");
        return (host + "-" + pid + "-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
