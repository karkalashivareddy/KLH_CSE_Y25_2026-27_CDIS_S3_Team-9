package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.events.messaging.EventPublisher;
import com.cloudresilience.modules.events.messaging.EventTopology;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Durable quarantine and controlled replay for exhausted or poison messages.
 * Replay publishes first and then records the audit row, so a broker call is
 * never made while a database transaction is held. Duplicate replay is safe
 * because consumers deduplicate by event id.
 */
@Service
public class DeadLetterService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EventEnvelopeCodec codec;
    private final EventPublisher publisher;

    public DeadLetterService(JdbcTemplate jdbc, TransactionTemplate transactions, EventEnvelopeCodec codec,
            EventPublisher publisher) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.codec = codec;
        this.publisher = publisher;
    }

    public void quarantine(EventEnvelope envelope, String routingKey, String consumerName, String errorCategory) {
        jdbc.update("""
                INSERT INTO dead_letter_messages(id,event_id,consumer_name,event_type,routing_key,payload,
                    error_category)
                VALUES(?,?,?,?,?,?::jsonb,?)
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """, UUID.randomUUID(), envelope.eventId(), consumerName, envelope.eventType(), routingKey,
                codec.encodeToString(envelope), errorCategory);
    }

    public void quarantineRaw(byte[] body, UUID eventId, String eventType, String routingKey, String consumerName,
            String errorCategory) {
        String payload = "{\"undecodableBase64\":\"" + Base64.getEncoder().encodeToString(
                body == null ? new byte[0] : body) + "\"}";
        jdbc.update("""
                INSERT INTO dead_letter_messages(id,event_id,consumer_name,event_type,routing_key,payload,
                    error_category)
                VALUES(?,?,?,?,?,?::jsonb,?)
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """, UUID.randomUUID(), eventId, consumerName, eventType, routingKey, payload, errorCategory);
    }

    public List<EventViews.DeadLetter> list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        String state = status == null || status.isBlank() ? null : status.strip().toUpperCase(java.util.Locale.ROOT);
        if (state != null && !java.util.Set.of("QUARANTINED", "REPLAYED", "DISCARDED").contains(state))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dead-letter status");
        return jdbc.query("""
                SELECT id,event_id,consumer_name,event_type,status,error_category,created_at,replayed_at
                FROM dead_letter_messages WHERE (? IS NULL OR status=?)
                ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new EventViews.DeadLetter(rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class), rs.getString("consumer_name"), rs.getString("event_type"),
                rs.getString("status"), rs.getString("error_category"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("replayed_at") == null ? null : rs.getTimestamp("replayed_at").toInstant()),
                state, state, size, Math.multiplyExact(page, size));
    }

    public EventViews.ReplayResult replay(UUID deadLetterId, UUID actorId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A replay reason of at most 500 characters is required");
        var rows = jdbc.query("""
                SELECT id,event_id,consumer_name,event_type,routing_key,payload::text AS payload,status
                FROM dead_letter_messages WHERE id=?
                """, (rs, row) -> new ReplayRow(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getString("consumer_name"), rs.getString("event_type"), rs.getString("routing_key"),
                rs.getString("payload"), rs.getString("status")), deadLetterId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead letter not found");
        ReplayRow row = rows.getFirst();
        if (!"QUARANTINED".equals(row.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only quarantined messages can be replayed");
        EventEnvelope envelope;
        try {
            envelope = codec.decode(row.payload().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException undecodable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quarantined payload cannot be replayed");
        }
        String outcome;
        try {
            publisher.publish(row.payload().getBytes(StandardCharsets.UTF_8), envelope.eventId(), envelope.eventType(),
                    envelope.eventVersion(), envelope.correlationId(), row.routingKey());
            outcome = "PUBLISHED";
        } catch (RuntimeException publishFailure) {
            outcome = "PUBLISH_FAILED";
        }
        String auditOutcome = outcome;
        String auditReason = reason.strip();
        transactions.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO dead_letter_replays(id,dead_letter_id,actor_user_id,reason,outcome) VALUES(?,?,?,?,?)",
                    UUID.randomUUID(), deadLetterId, actorId, auditReason, auditOutcome);
            if ("PUBLISHED".equals(auditOutcome))
                jdbc.update("UPDATE dead_letter_messages SET status='REPLAYED',replayed_at=CURRENT_TIMESTAMP WHERE id=? AND status='QUARANTINED'",
                        deadLetterId);
        });
        if (!"PUBLISHED".equals(outcome))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Replay could not be published");
        return new EventViews.ReplayResult(deadLetterId, outcome);
    }

    public EventViews.OutboxSummary outboxSummary() {
        var counts = jdbc.query("SELECT status,count(*) total FROM outbox_events GROUP BY status",
                (rs, row) -> java.util.Map.entry(rs.getString("status"), rs.getLong("total"))).stream()
                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
        Long oldest = jdbc.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - min(created_at)))::bigint, 0)
                FROM outbox_events WHERE status IN ('PENDING','PROCESSING')
                """, Long.class);
        return new EventViews.OutboxSummary(counts.getOrDefault("PENDING", 0L), counts.getOrDefault("PROCESSING", 0L),
                counts.getOrDefault("PUBLISHED", 0L), counts.getOrDefault("FAILED", 0L), oldest);
    }

    private record ReplayRow(UUID id, UUID eventId, String consumerName, String eventType, String routingKey,
            String payload, String status) {}
}
