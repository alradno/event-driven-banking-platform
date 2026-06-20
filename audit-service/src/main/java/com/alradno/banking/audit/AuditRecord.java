package com.alradno.banking.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
public class AuditRecord {
    @Id
    private UUID id;
    private String eventId;
    private String eventType;
    private String subject;
    private String correlationId;
    private String traceId;
    private Instant occurredAt;
    @Column(columnDefinition = "text")
    private String evidence;

    protected AuditRecord() {
    }

    public AuditRecord(
            UUID id,
            String eventId,
            String eventType,
            String subject,
            String correlationId,
            String traceId,
            Instant occurredAt,
            String evidence) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.subject = subject;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
        this.evidence = evidence;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSubject() {
        return subject;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEvidence() {
        return evidence;
    }
}
