package com.alradno.banking.common.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {
    @Test
    void requiresTraceableMetadata() {
        assertThrows(IllegalArgumentException.class, () -> EventEnvelope.of(
                "payment.created",
                1,
                "payment-service",
                "",
                null,
                "trace-1",
                "payment/1",
                Map.of()));
    }

    @Test
    void keepsPayloadImmutable() {
        EventEnvelope envelope = EventEnvelope.of(
                "payment.completed",
                1,
                "payment-service",
                "corr-1",
                "payment/1",
                "trace-1",
                "payment/1",
                Map.of("status", "COMPLETED"));

        assertEquals("COMPLETED", envelope.payload().get("status"));
        assertThrows(UnsupportedOperationException.class, () -> envelope.payload().put("status", "FAILED"));
    }
}
