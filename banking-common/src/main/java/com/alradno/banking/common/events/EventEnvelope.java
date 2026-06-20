package com.alradno.banking.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String causationId,
        String traceId,
        String subject,
        Map<String, Object> payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId is required");
        if (eventVersion != EventCompatibility.CURRENT_ENVELOPE_VERSION) {
            throw new IllegalArgumentException("unsupported event envelope version " + eventVersion);
        }
        requireText(eventType, "eventType");
        requireText(producer, "producer");
        requireText(correlationId, "correlationId");
        requireText(traceId, "traceId");
        requireText(subject, "subject");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static EventEnvelope of(
            String eventType,
            int eventVersion,
            String producer,
            String correlationId,
            String causationId,
            String traceId,
            String subject,
            Map<String, Object> payload) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                eventVersion,
                Instant.now(),
                producer,
                correlationId,
                causationId,
                traceId,
                subject,
                payload);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
