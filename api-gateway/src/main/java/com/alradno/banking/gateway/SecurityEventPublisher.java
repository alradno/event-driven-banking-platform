package com.alradno.banking.gateway;

import com.alradno.banking.common.events.EventCompatibility;
import com.alradno.banking.common.events.EventEnvelope;
import com.alradno.banking.common.http.Correlation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityEventPublisher.class);
    private static final String TOPIC = "bank.security.events";
    private static final String PRODUCER = "api-gateway";
    static final String LOGIN_FAILED = "security.login_failed";
    static final String ACCESS_DENIED = "security.access_denied";
    static final String RATE_LIMIT_EXCEEDED = "security.rate_limit_exceeded";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SecurityEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> publish(ServerWebExchange exchange, String eventType, String reason) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .doOnNext(subject -> publishEvent(exchange, eventType, reason, subject))
                .then();
    }

    private void publishEvent(ServerWebExchange exchange, String eventType, String reason, String subject) {
        try {
            String correlationId = correlationId(exchange);
            String traceId = traceId(exchange);
            setResponseCorrelationHeaders(exchange, correlationId, traceId);

            EventEnvelope event = EventEnvelope.of(
                    eventType,
                    EventCompatibility.CURRENT_ENVELOPE_VERSION,
                    PRODUCER,
                    correlationId,
                    null,
                    traceId,
                    subject.isBlank() ? "anonymous" : subject,
                    payload(exchange, reason));
            String raw = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.subject(), raw).whenComplete((result, error) -> {
                if (error != null) {
                    LOGGER.warn("failed to publish security event {}", eventType, error);
                }
            });
        } catch (Exception ex) {
            LOGGER.warn("failed to build security event {}", eventType, ex);
        }
    }

    private Map<String, Object> payload(ServerWebExchange exchange, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", exchange.getRequest().getMethod().name());
        payload.put("path", exchange.getRequest().getPath().pathWithinApplication().value());
        payload.put("status", status(exchange));
        payload.put("reason", reason);
        payload.put("remoteAddress", remoteAddress(exchange));
        return payload;
    }

    private int status(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status == null ? 0 : status.value();
    }

    private String remoteAddress(ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() == null) {
            return "unknown";
        }
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    private String correlationId(ServerWebExchange exchange) {
        String fromRequest = exchange.getRequest().getHeaders().getFirst(Correlation.CORRELATION_ID);
        String fromResponse = exchange.getResponse().getHeaders().getFirst(Correlation.CORRELATION_ID);
        return Correlation.presentOrNew(fromRequest == null ? fromResponse : fromRequest);
    }

    private String traceId(ServerWebExchange exchange) {
        String fromRequest = exchange.getRequest().getHeaders().getFirst(Correlation.TRACE_ID);
        String fromResponse = exchange.getResponse().getHeaders().getFirst(Correlation.TRACE_ID);
        return Correlation.presentOrNew(fromRequest == null ? fromResponse : fromRequest);
    }

    private void setResponseCorrelationHeaders(ServerWebExchange exchange, String correlationId, String traceId) {
        if (!exchange.getResponse().isCommitted()) {
            exchange.getResponse().getHeaders().set(Correlation.CORRELATION_ID, correlationId);
            exchange.getResponse().getHeaders().set(Correlation.TRACE_ID, traceId);
        }
    }
}
