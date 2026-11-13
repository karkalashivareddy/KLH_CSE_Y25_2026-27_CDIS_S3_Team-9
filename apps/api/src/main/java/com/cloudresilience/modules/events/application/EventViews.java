package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import java.time.Instant;
import java.util.UUID;

public final class EventViews {
    private EventViews() {}

    public record Page<T>(java.util.List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record OutboxSummary(long pending, long processing, long published, long failed,
            Long oldestPendingAgeSeconds) {}

    public record DeadLetter(UUID id, UUID eventId, String consumerName, String eventType, String status,
            String errorCategory, Instant createdAt, Instant replayedAt) {}

    public record ReplayResult(UUID deadLetterId, String outcome) {}

    public record Notification(UUID id, UUID orderId, String eventType, String title, String body,
            Instant readAt, Instant createdAt) {}
}
