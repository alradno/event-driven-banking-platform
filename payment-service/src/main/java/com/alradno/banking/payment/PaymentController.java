package com.alradno.banking.payment;

import com.alradno.banking.common.http.Correlation;
import com.alradno.banking.payment.api.PaymentRequest;
import com.alradno.banking.payment.api.PaymentResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentWorkflowService workflow;

    public PaymentController(PaymentWorkflowService workflow) {
        this.workflow = workflow;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @RequestHeader(Correlation.CUSTOMER_ID) UUID customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = Correlation.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = Correlation.TRACE_ID, required = false) String traceId,
            @Valid @RequestBody PaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        return workflow.create(
                customerId,
                idempotencyKey,
                Correlation.presentOrNew(correlationId),
                Correlation.presentOrNew(traceId),
                request);
    }

    @GetMapping
    public List<PaymentResponse> list(@RequestHeader(Correlation.CUSTOMER_ID) UUID customerId) {
        return workflow.list(customerId);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id, @RequestHeader(Correlation.CUSTOMER_ID) UUID customerId) {
        return workflow.get(id, customerId);
    }

    @DeleteMapping("/{id}")
    public PaymentResponse cancel(@PathVariable UUID id, @RequestHeader(Correlation.CUSTOMER_ID) UUID customerId) {
        return workflow.cancel(id, customerId);
    }
}
