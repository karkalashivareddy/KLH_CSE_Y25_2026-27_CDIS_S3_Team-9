package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.events.domain.EventType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the inbox insert and the consumer's durable effect in one transaction.
 * A duplicate delivery therefore creates exactly one logical effect, and a
 * crash before commit leaves no inbox row, so the broker can safely redeliver.
 */
@Service
public class EventConsumptionService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public EventConsumptionService(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public boolean consume(EventEnvelope envelope, String consumerName, Runnable effect) {
        if (envelope.eventVersion() != EventType.CURRENT_VERSION)
            throw new UnsupportedEventVersionException(envelope.eventId(), envelope.eventVersion());
        EventType.fromWireName(envelope.eventType());
        Boolean applied = transactions.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO processed_events(event_id,consumer_name,outcome) VALUES(?,?,'PROCESSED')
                    ON CONFLICT (event_id,consumer_name) DO NOTHING
                    """, envelope.eventId(), consumerName);
            if (inserted == 0) return false;
            effect.run();
            return true;
        });
        return Boolean.TRUE.equals(applied);
    }

    public static class UnsupportedEventVersionException extends RuntimeException {
        private final java.util.UUID eventId;

        public UnsupportedEventVersionException(java.util.UUID eventId, int version) {
            super("Unsupported event version " + version);
            this.eventId = eventId;
        }

        public java.util.UUID eventId() { return eventId; }
    }
}
