package com.alradno.banking.gateway;

import com.alradno.banking.common.http.Correlation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.security.Principal;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CorrelationAndClaimsFilter implements GlobalFilter, Ordered {
    private final ObjectProvider<Tracer> tracer;

    public CorrelationAndClaimsFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = Correlation.presentOrNew(exchange.getRequest().getHeaders().getFirst(Correlation.CORRELATION_ID));
        String traceId = Correlation.presentOrNew(exchange.getRequest().getHeaders().getFirst(Correlation.TRACE_ID));
        tagCurrentSpan(correlationId, traceId);

        return exchange.getPrincipal()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(principal -> chain.filter(withHeaders(
                        exchange,
                        correlationId,
                        traceId,
                        principal.orElse(null))));
    }

    private ServerWebExchange withHeaders(
            ServerWebExchange exchange,
            String correlationId,
            String traceId,
            Principal principal) {
        ServerHttpRequest.Builder request = exchange.getRequest().mutate()
                .header(Correlation.CORRELATION_ID, correlationId)
                .header(Correlation.TRACE_ID, traceId);

        if (principal instanceof Authentication authentication
                && authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String subject = claimOrDefault(jwtAuthentication, "preferred_username", jwtAuthentication.getToken().getSubject());
            String customerId = claimOrDefault(jwtAuthentication, "customer_id", "");
            request.header(Correlation.USER_SUBJECT, subject);
            if (!customerId.isBlank()) {
                request.header(Correlation.CUSTOMER_ID, customerId);
            }
        } else if (principal != null) {
            request.header(Correlation.USER_SUBJECT, principal.getName());
        }

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(Correlation.CORRELATION_ID, correlationId);
            exchange.getResponse().getHeaders().set(Correlation.TRACE_ID, traceId);
            return Mono.empty();
        });
        return exchange.mutate().request(request.build()).build();
    }

    private void tagCurrentSpan(String correlationId, String traceId) {
        Tracer activeTracer = tracer.getIfAvailable();
        Span currentSpan = activeTracer == null ? null : activeTracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("banking.correlation_id", correlationId);
            currentSpan.tag("banking.trace_id", traceId);
        }
    }

    private String claimOrDefault(JwtAuthenticationToken token, String claimName, String fallback) {
        Object value = token.getTokenAttributes().get(claimName);
        return value == null ? fallback : value.toString();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
