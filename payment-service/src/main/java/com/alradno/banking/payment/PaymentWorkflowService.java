package com.alradno.banking.payment;

import com.alradno.banking.common.events.EventEnvelope;
import com.alradno.banking.common.money.MoneyRules;
import com.alradno.banking.payment.AccountTransferClient.TransferRequest;
import com.alradno.banking.payment.AccountTransferClient.TransferResponse;
import com.alradno.banking.payment.api.PaymentRequest;
import com.alradno.banking.payment.api.PaymentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentWorkflowService {
    private static final String PAYMENT_TOPIC = "bank.payment.events";

    private final PaymentRepository payments;
    private final OutboxEventRepository outbox;
    private final AccountTransferClient accounts;
    private final ObjectMapper objectMapper;

    public PaymentWorkflowService(
            PaymentRepository payments,
            OutboxEventRepository outbox,
            AccountTransferClient accounts,
            ObjectMapper objectMapper) {
        this.payments = payments;
        this.outbox = outbox;
        this.accounts = accounts;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse create(
            UUID customerId,
            String idempotencyKey,
            String correlationId,
            String traceId,
            PaymentRequest request) {
        String requestHash = PaymentRequestHasher.hash(request);
        Payment payment = payments.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(existing -> requireSameRequest(existing, requestHash))
                .orElseGet(() -> createNew(customerId, idempotencyKey, correlationId, traceId, request, requestHash));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id, UUID customerId) {
        Payment payment = payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found"));
        if (!payment.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "payment owner mismatch");
        }
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(UUID customerId) {
        return payments.findAll().stream()
                .filter(payment -> payment.getCustomerId().equals(customerId))
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional
    public PaymentResponse cancel(UUID id, UUID customerId) {
        Payment payment = payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found"));
        if (!payment.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "payment owner mismatch");
        }
        if (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "payment cannot be cancelled after processing");
        }
        payment.cancel();
        recordEvent(payment, "payment.cancelled");
        return PaymentResponse.from(payment);
    }

    private Payment createNew(
            UUID customerId,
            String idempotencyKey,
            String correlationId,
            String traceId,
            PaymentRequest request,
            String requestHash) {
        Payment payment = new Payment(
                UUID.randomUUID(),
                customerId,
                request.sourceAccountId(),
                request.targetAccountId(),
                request.currency(),
                request.amountMinor(),
                idempotencyKey,
                requestHash,
                correlationId,
                traceId);
        payments.save(payment);
        recordEvent(payment, "payment.created");

        try {
            MoneyRules.requirePositiveMinor(request.amountMinor());
            payment.transitionTo(PaymentStatus.VALIDATED);
            recordEvent(payment, "payment.validated");
            payment.transitionTo(PaymentStatus.PROCESSING);
            recordEvent(payment, "payment.processing");

            TransferResponse transfer = accounts.transfer(new TransferRequest(
                    request.sourceAccountId(),
                    request.targetAccountId(),
                    customerId,
                    request.currency(),
                    request.amountMinor(),
                    correlationId));

            if (transfer == null || !transfer.approved()) {
                String reason = transfer == null ? "ACCOUNT_SERVICE_NO_RESPONSE" : transfer.reason();
                payment.reject(reason);
                recordEvent(payment, "payment.rejected");
            } else {
                payment.transitionTo(PaymentStatus.COMPLETED);
                recordEvent(payment, "payment.completed");
            }
        } catch (IllegalArgumentException ex) {
            payment.reject(ex.getMessage());
            recordEvent(payment, "payment.rejected");
        } catch (RuntimeException ex) {
            payment.fail("ACCOUNT_SERVICE_UNAVAILABLE");
            recordEvent(payment, "payment.failed");
        }
        return payment;
    }

    private Payment requireSameRequest(Payment existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency key reused with different request body");
        }
        return existing;
    }

    private void recordEvent(Payment payment, String eventType) {
        EventEnvelope envelope = EventEnvelope.of(
                eventType,
                1,
                "payment-service",
                payment.getCorrelationId(),
                "payment/" + payment.getId(),
                payment.getTraceId(),
                "payment/" + payment.getId(),
                Map.of(
                        "paymentId", payment.getId().toString(),
                        "customerId", payment.getCustomerId().toString(),
                        "sourceAccountId", payment.getSourceAccountId().toString(),
                        "targetAccountId", payment.getTargetAccountId().toString(),
                        "currency", payment.getCurrency().name(),
                        "amountMinor", payment.getAmountMinor(),
                        "status", payment.getStatus().name(),
                        "reason", payment.getRejectionReason() == null ? "" : payment.getRejectionReason()));
        try {
            outbox.save(new OutboxEvent(
                    payment.getId().toString(),
                    PAYMENT_TOPIC,
                    eventType,
                    objectMapper.writeValueAsString(envelope)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize payment event", ex);
        }
    }
}
