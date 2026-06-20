package com.alradno.banking.payment.api;

import com.alradno.banking.common.money.CurrencyCode;
import com.alradno.banking.payment.Payment;
import com.alradno.banking.payment.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID customerId,
        UUID sourceAccountId,
        UUID targetAccountId,
        CurrencyCode currency,
        long amountMinor,
        PaymentStatus status,
        String rejectionReason,
        String correlationId,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getCustomerId(),
                payment.getSourceAccountId(),
                payment.getTargetAccountId(),
                payment.getCurrency(),
                payment.getAmountMinor(),
                payment.getStatus(),
                payment.getRejectionReason(),
                payment.getCorrelationId(),
                payment.getTraceId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
