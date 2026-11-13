package com.cloudresilience.modules.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudresilience.modules.events.application.EventEnvelopeCodec;
import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.events.domain.EventType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EventEnvelopeCodecTest {
    private final EventEnvelopeCodec codec = new EventEnvelopeCodec(new ObjectMapper());

    @Test
    void roundTripsVersionedEnvelopeWithoutLosingFields() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("orderNumber", "CR-ABC");
        payload.put("status", "CONFIRMED");
        payload.put("totalAmount", "24.70");
        EventEnvelope original = new EventEnvelope(eventId, EventType.ORDER_PLACED.wireName(),
                EventType.CURRENT_VERSION, "CustomerOrder", orderId, Instant.parse("2026-11-13T10:15:30Z"),
                orderId, null, payload);

        EventEnvelope decoded = codec.decode(codec.encode(original));

        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.eventType()).isEqualTo("OrderPlaced");
        assertThat(decoded.eventVersion()).isEqualTo(1);
        assertThat(decoded.aggregateType()).isEqualTo("CustomerOrder");
        assertThat(decoded.aggregateId()).isEqualTo(orderId);
        assertThat(decoded.occurredAt()).isEqualTo(Instant.parse("2026-11-13T10:15:30Z"));
        assertThat(decoded.payload()).containsEntry("orderNumber", "CR-ABC");
    }

    @Test
    void rejectsEmptyOrMalformedBodies() {
        assertThatThrownBy(() -> codec.decode(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> codec.decode("{\"eventId\":\"not-a-uuid\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(EventEnvelopeCodec.InvalidEventException.class);
    }

    @Test
    void envelopeRejectsMissingRequiredFieldsAndNegativeVersions() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new EventEnvelope(null, "OrderPlaced", 1, "CustomerOrder", id, Instant.now(), null,
                null, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventEnvelope(id, "OrderPlaced", 0, "CustomerOrder", id, Instant.now(), null,
                null, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
