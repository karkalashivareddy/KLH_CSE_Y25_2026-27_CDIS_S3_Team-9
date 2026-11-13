package com.cloudresilience.modules.events.persistence;

import java.util.UUID;

/** Mutable publication state of a persisted outbox row. */
public record OutboxEvent(UUID id, UUID aggregateId, String eventType, int eventVersion, String payload,
        int attemptCount, UUID correlationId) {}
