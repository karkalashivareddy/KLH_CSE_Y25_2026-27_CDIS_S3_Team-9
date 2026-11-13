package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.events.domain.EventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes an event to the transactional outbox. Callers invoke this inside the
 * same database transaction as the business change, so a committed order always
 * has its event and a rolled-back order never leaves a publishable event.
 */
@Service
public class OutboxWriter {
    private final JdbcTemplate jdbc;
    private final EventEnvelopeCodec codec;

    public OutboxWriter(JdbcTemplate jdbc, EventEnvelopeCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    public UUID append(EventType type, UUID aggregateId, Map<String, Object> payload, UUID correlationId,
            UUID causationId) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        EventEnvelope envelope = new EventEnvelope(eventId, type.wireName(), EventType.CURRENT_VERSION,
                "CustomerOrder", aggregateId, occurredAt, correlationId, causationId, payload);
        jdbc.update("""
                INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,
                    occurred_at,correlation_id,causation_id)
                VALUES(?,'CustomerOrder',?,?,?,?::jsonb,?,?,?)
                """, eventId, aggregateId, type.wireName(), EventType.CURRENT_VERSION,
                codec.encodeToString(envelope), java.sql.Timestamp.from(occurredAt), correlationId, causationId);
        return eventId;
    }
}
