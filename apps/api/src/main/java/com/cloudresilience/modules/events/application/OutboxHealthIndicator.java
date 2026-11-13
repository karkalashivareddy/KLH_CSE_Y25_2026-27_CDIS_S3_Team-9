package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.persistence.OutboxRepository;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes outbox backlog as an operational health component. A backlog does not
 * fail application readiness: committed orders remain correct while the broker
 * is unavailable, and the publisher drains the backlog on recovery.
 */
@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {
    private final OutboxRepository repository;

    public OutboxHealthIndicator(OutboxRepository repository) { this.repository = repository; }

    @Override
    public Health health() {
        try {
            Map<String, Long> counts = repository.statusCounts();
            long pending = counts.getOrDefault("PENDING", 0L);
            long processing = counts.getOrDefault("PROCESSING", 0L);
            long failed = counts.getOrDefault("FAILED", 0L);
            Long oldest = repository.oldestPendingAgeSeconds();
            Health.Builder builder = failed > 0 ? Health.status("DEGRADED") : Health.up();
            return builder
                    .withDetail("pending", pending)
                    .withDetail("processing", processing)
                    .withDetail("published", counts.getOrDefault("PUBLISHED", 0L))
                    .withDetail("failed", failed)
                    .withDetail("oldestPendingAgeSeconds", oldest == null ? 0 : oldest)
                    .build();
        } catch (RuntimeException databaseUnavailable) {
            return Health.down().withDetail("error", "outbox state unavailable").build();
        }
    }
}
