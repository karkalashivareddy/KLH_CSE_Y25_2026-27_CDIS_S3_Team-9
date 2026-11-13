package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** JSON codec for the versioned event envelope. */
@Component
public class EventEnvelopeCodec {
    private final ObjectMapper mapper;

    public EventEnvelopeCodec(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] encode(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Event envelope could not be serialized", failure);
        }
    }

    public String encodeToString(EventEnvelope envelope) {
        return new String(encode(envelope), StandardCharsets.UTF_8);
    }

    public EventEnvelope decode(byte[] body) {
        if (body == null || body.length == 0) throw new IllegalArgumentException("Event body is empty");
        try {
            EventEnvelope envelope = mapper.readValue(body, EventEnvelope.class);
            if (envelope == null) throw new IllegalArgumentException("Event body is null");
            return envelope;
        } catch (RuntimeException failure) {
            throw new InvalidEventException("Event body is not a valid envelope", failure);
        }
    }

    public static class InvalidEventException extends RuntimeException {
        public InvalidEventException(String message, Throwable cause) { super(message, cause); }
    }
}
