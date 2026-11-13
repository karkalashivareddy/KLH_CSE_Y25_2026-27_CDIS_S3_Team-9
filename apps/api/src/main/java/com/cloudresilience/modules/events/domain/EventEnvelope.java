package com.cloudresilience.modules.events.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stable event envelope. The payload must contain only data required by
 * downstream consumers: identifiers, order status and amounts. Credentials,
 * session identifiers, CSRF tokens, payment credentials and personal data are
 * never included.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        Map<String, Object> payload) {

    public EventEnvelope {
        if (eventId == null || eventType == null || aggregateType == null || aggregateId == null
                || occurredAt == null || payload == null) {
            throw new IllegalArgumentException("Event envelope is missing required fields");
        }
        if (eventVersion < 1) throw new IllegalArgumentException("Event version must be positive");
        payload = Map.copyOf(payload);
    }
}
