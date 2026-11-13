package com.cloudresilience.modules.events.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read models for persisted notifications and the order activity projection. */
@Service
public class EngagementQueryService {
    private final JdbcTemplate jdbc;

    public EngagementQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public EventViews.Page<EventViews.Notification> notifications(UUID customerId, int page, int size) {
        validate(page, size);
        long total = jdbc.queryForObject("SELECT count(*) FROM in_app_notifications WHERE customer_id=?",
                Long.class, customerId);
        List<EventViews.Notification> content = jdbc.query("""
                SELECT id,order_id,event_type,title,body,read_at,created_at FROM in_app_notifications
                WHERE customer_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new EventViews.Notification(rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class), rs.getString("event_type"), rs.getString("title"),
                rs.getString("body"), rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()), customerId, size, Math.multiplyExact(page, size));
        return new EventViews.Page<>(content, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional
    public void markRead(UUID customerId, UUID notificationId) {
        int changed = jdbc.update("""
                UPDATE in_app_notifications SET read_at=CURRENT_TIMESTAMP
                WHERE id=? AND customer_id=? AND read_at IS NULL
                """, notificationId, customerId);
        if (changed == 0) {
            Integer exists = jdbc.queryForObject("""
                    SELECT count(*) FROM in_app_notifications WHERE id=? AND customer_id=?
                    """, Integer.class, notificationId, customerId);
            if (exists == null || exists == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
    }

    @Transactional(readOnly = true)
    public List<Activity> orderActivity(UUID orderId) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM customer_orders WHERE id=?", Integer.class, orderId);
        if (exists == null || exists == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return jdbc.query("""
                SELECT id,event_id,event_type,order_status,occurred_at,recorded_at FROM order_activity
                WHERE order_id=? ORDER BY occurred_at,id
                """, (rs, row) -> new Activity(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getString("event_type"), rs.getString("order_status"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("recorded_at").toInstant()), orderId);
    }

    public record Activity(UUID id, UUID eventId, String eventType, String orderStatus, Instant occurredAt,
            Instant recordedAt) {}

    private void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
    }
}
