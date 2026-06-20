package com.alradno.banking.payment;

import com.alradno.banking.common.money.CurrencyCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency", columnNames = {"customer_id", "idempotency_key"}))
public class Payment {
    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private long amountMinor;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String rejectionReason;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(nullable = false)
    private String requestHash;
    private String correlationId;
    private String traceId;
    private Instant createdAt;
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(
            UUID id,
            UUID customerId,
            UUID sourceAccountId,
            UUID targetAccountId,
            CurrencyCode currency,
            long amountMinor,
            String idempotencyKey,
            String requestHash,
            String correlationId,
            String traceId) {
        this.id = id;
        this.customerId = customerId;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.status = PaymentStatus.CREATED;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void transitionTo(PaymentStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        this.status = PaymentStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.rejectionReason = reason;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
