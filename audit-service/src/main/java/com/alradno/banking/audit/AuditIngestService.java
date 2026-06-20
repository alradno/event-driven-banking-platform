package com.alradno.banking.audit;

import com.alradno.banking.common.events.EventCompatibility;
import com.alradno.banking.common.events.EventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AuditIngestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditIngestService.class);
    private static final Set<String> REDACTED_FIELDS = Set.of("password", "token", "secret", "pan", "cvv");

    private final AuditRecordRepository records;
    private final ObjectMapper objectMapper;

    public AuditIngestService(AuditRecordRepository records, ObjectMapper objectMapper) {
        this.records = records;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            id = "audit-service",
            topics = {
                "bank.customer.events",
                "bank.account.events",
                "bank.payment.events",
                "bank.security.events",
                "bank.notification.events"
            })
    public void ingest(String raw) {
        try {
            EventEnvelope envelope = objectMapper.readValue(raw, EventEnvelope.class);
            EventCompatibility.requireSupported(envelope);
            records.save(new AuditRecord(
                    UUID.randomUUID(),
                    envelope.eventId().toString(),
                    envelope.eventType(),
                    envelope.subject(),
                    envelope.correlationId(),
                    envelope.traceId(),
                    envelope.occurredAt(),
                    sanitizePayload(raw)));
        } catch (Exception ex) {
            LOGGER.warn("audit ingest skipped malformed event", ex);
        }
    }

    public AuditRecord ingestDirect(DirectAuditRequest request) {
        AuditRecord record = new AuditRecord(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                request.eventType(),
                request.subject(),
                request.correlationId(),
                request.traceId(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt(),
                request.evidence());
        return records.save(record);
    }

    private String sanitizePayload(String raw) throws Exception {
        JsonNode node = objectMapper.readTree(raw);
        for (String field : REDACTED_FIELDS) {
            if (node.findValue(field) != null) {
                return "{\"redacted\":true,\"reason\":\"sensitive field present\"}";
            }
        }
        return raw;
    }

    public record DirectAuditRequest(
            String eventType,
            String subject,
            String correlationId,
            String traceId,
            Instant occurredAt,
            String evidence) {
    }
}
