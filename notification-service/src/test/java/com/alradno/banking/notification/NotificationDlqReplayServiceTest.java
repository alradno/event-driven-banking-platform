package com.alradno.banking.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alradno.banking.common.events.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationDlqReplayServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void matchesEnvelopeCorrelationId() throws Exception {
        NotificationDlqReplayService service = service();
        EventEnvelope envelope = EventEnvelope.of(
                "payment.completed",
                1,
                "payment-service",
                "correlation-123",
                "payment/1",
                "trace-123",
                "payment/1",
                Map.of("paymentId", "1"));

        String raw = objectMapper.writeValueAsString(envelope);

        assertTrue(service.matchesCorrelationId(raw, "correlation-123"));
        assertFalse(service.matchesCorrelationId(raw, "other-correlation"));
    }

    @Test
    void ignoresMalformedDlqPayloads() {
        assertFalse(service().matchesCorrelationId("not-json", "correlation-123"));
    }

    private NotificationDlqReplayService service() {
        return new NotificationDlqReplayService(null, null, objectMapper);
    }
}
