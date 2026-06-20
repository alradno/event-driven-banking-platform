package com.alradno.banking.common.events;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class EventEnvelopeCompatibilityTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsVersionOnePaymentFixtureWithUnknownPayloadFields() throws Exception {
        EventEnvelope envelope = readFixture("payment-completed.json");

        assertVersionOneFixture(envelope, "payment.completed", "payment-service");
        assertEquals("COMPLETED", envelope.payload().get("status"));
        assertEquals("payload consumers ignore unknown fields", envelope.payload().get("schemaCompatibilityCanary"));
    }

    @Test
    void readsVersionOneSecurityFixtureWithUnknownPayloadFields() throws Exception {
        EventEnvelope envelope = readFixture("security-access-denied.json");

        assertVersionOneFixture(envelope, "security.access_denied", "api-gateway");
        assertEquals("DENIED", envelope.payload().get("decision"));
        assertEquals("payload consumers ignore unknown fields", envelope.payload().get("schemaCompatibilityCanary"));
    }

    @Test
    void rejectsUnsupportedEnvelopeVersions() {
        assertThrows(IllegalArgumentException.class, () -> new EventEnvelope(
                java.util.UUID.randomUUID(),
                "payment.completed",
                99,
                java.time.Instant.now(),
                "payment-service",
                "correlation-unsupported",
                "payment/1",
                "trace-unsupported",
                "payment/1",
                java.util.Map.of()));
    }

    @Test
    void rejectsMissingEnvelopeVersionDuringDeserialization() {
        assertThrows(Exception.class, () -> objectMapper.readValue("""
                {
                  "eventId": "00000000-0000-0000-0000-000000000199",
                  "eventType": "payment.completed",
                  "occurredAt": "2026-06-20T10:00:00Z",
                  "producer": "payment-service",
                  "correlationId": "fixture-correlation-missing-version",
                  "causationId": "payment/1",
                  "traceId": "trace-missing-version",
                  "subject": "payment/1",
                  "payload": {}
                }
                """, EventEnvelope.class));
    }

    private EventEnvelope readFixture(String fixtureName) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/events/v1/" + fixtureName)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + fixtureName);
            }
            return objectMapper.readValue(stream, EventEnvelope.class);
        }
    }

    private void assertVersionOneFixture(EventEnvelope envelope, String eventType, String producer) {
        assertDoesNotThrow(() -> EventCompatibility.requireSupported(envelope));
        assertEquals(EventCompatibility.CURRENT_ENVELOPE_VERSION, envelope.eventVersion());
        assertEquals(eventType, envelope.eventType());
        assertEquals(producer, envelope.producer());
        assertTrue(envelope.eventId() != null);
        assertTrue(envelope.occurredAt() != null);
        assertTrue(!envelope.correlationId().isBlank());
        assertTrue(!envelope.traceId().isBlank());
        assertTrue(!envelope.subject().isBlank());
    }
}
