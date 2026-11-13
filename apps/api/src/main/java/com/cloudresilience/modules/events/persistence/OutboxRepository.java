package com.cloudresilience.modules.events.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Atomically leases due rows. {@code FOR UPDATE SKIP LOCKED} prevents two
     * publishers from claiming the same event, and expired PROCESSING leases
     * are reclaimed so a crashed publisher cannot strand an event.
     */
    public List<OutboxEvent> claimDue(int batchSize, String owner, int leaseSeconds) {
        return jdbc.query("""
                WITH claimed AS (
                    SELECT id FROM outbox_events
                    WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND lease_expires_at < CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at, created_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events target
                SET status = 'PROCESSING', lease_owner = ?,
                    lease_expires_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                FROM claimed
                WHERE target.id = claimed.id
                RETURNING target.id, target.aggregate_id, target.event_type, target.event_version,
                          target.payload::text AS payload, target.attempt_count, target.correlation_id
                """, (rs, row) -> new OutboxEvent(rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"), rs.getInt("event_version"), rs.getString("payload"),
                rs.getInt("attempt_count"), rs.getObject("correlation_id", UUID.class)),
                batchSize, owner, leaseSeconds);
    }

    public boolean markPublished(UUID id, String owner) {
        return jdbc.update("""
                UPDATE outbox_events SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                    lease_owner = NULL, lease_expires_at = NULL, last_error_category = NULL
                WHERE id = ? AND status = 'PROCESSING' AND lease_owner = ?
                """, id, owner) == 1;
    }

    public boolean markFailed(UUID id, String owner, String errorCategory, int nextAttemptSeconds, int maxAttempts) {
        return jdbc.update("""
                UPDATE outbox_events SET
                    status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    attempt_count = attempt_count + 1,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    lease_owner = NULL, lease_expires_at = NULL, last_error_category = ?
                WHERE id = ? AND status = 'PROCESSING' AND lease_owner = ?
                """, maxAttempts, nextAttemptSeconds, errorCategory, id, owner) == 1;
    }

    public Map<String, Long> statusCounts() {
        return jdbc.query("SELECT status, count(*) total FROM outbox_events GROUP BY status",
                (rs, row) -> Map.entry(rs.getString("status"), rs.getLong("total"))).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Long oldestPendingAgeSeconds() {
        return jdbc.queryForObject("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - min(created_at)))::bigint, 0)
                FROM outbox_events WHERE status IN ('PENDING','PROCESSING')
                """, Long.class);
    }

    public Instant oldestPendingCreatedAt() {
        return jdbc.queryForObject("""
                SELECT min(created_at) FROM outbox_events WHERE status IN ('PENDING','PROCESSING')
                """, (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant());
    }

    public long failedCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status='FAILED'", Long.class);
        return count == null ? 0 : count;
    }
}
