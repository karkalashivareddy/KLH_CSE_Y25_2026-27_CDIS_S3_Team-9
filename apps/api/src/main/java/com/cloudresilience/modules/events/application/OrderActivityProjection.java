package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Reporting projection fed by the activity consumer, separate from authoritative orders. */
@Service
public class OrderActivityProjection {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderActivityProjection(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void project(EventEnvelope envelope) {
        Map<String, Object> payload = envelope.payload();
        UUID orderId = UUID.fromString(text(payload.get("orderId")));
        String status = text(payload.get("status"));
        if (!java.util.Set.of("PENDING_PAYMENT", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED",
                "PAYMENT_FAILED").contains(status))
            throw new IllegalArgumentException("Event payload contains an invalid order status");
        jdbc.update("""
                INSERT INTO order_activity(id,order_id,event_id,event_type,order_status,occurred_at,details)
                VALUES(?,?,?,?,?,?,?::jsonb)
                ON CONFLICT (event_id) DO NOTHING
                """, UUID.randomUUID(), orderId, envelope.eventId(), envelope.eventType(), status,
                java.sql.Timestamp.from(envelope.occurredAt()), mapper.writeValueAsString(payload));
    }

    private String text(Object value) {
        if (value == null) throw new IllegalArgumentException("Event payload is missing a required field");
        return value.toString();
    }
}
