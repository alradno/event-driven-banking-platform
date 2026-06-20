package com.alradno.banking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.alradno.banking.common.http.Correlation;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationAndClaimsFilterTest {
    @Test
    void authenticatedPrincipalRunsChainOnceAndAddsClaimsHeaders() {
        CorrelationAndClaimsFilter filter = new CorrelationAndClaimsFilter(new EmptyTracerProvider());
        AtomicInteger chainCalls = new AtomicInteger();
        AtomicReference<ServerWebExchange> routedExchange = new AtomicReference<>();
        ServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/payments")
                        .header(Correlation.CORRELATION_ID, "corr-123")
                        .header(Correlation.TRACE_ID, "trace-456"))
                .mutate()
                .principal(Mono.just(jwtPrincipal()))
                .build();
        GatewayFilterChain chain = routed -> {
            chainCalls.incrementAndGet();
            routedExchange.set(routed);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalls).hasValue(1);
        assertThat(routedExchange.get().getRequest().getHeaders().getFirst(Correlation.CORRELATION_ID))
                .isEqualTo("corr-123");
        assertThat(routedExchange.get().getRequest().getHeaders().getFirst(Correlation.TRACE_ID))
                .isEqualTo("trace-456");
        assertThat(routedExchange.get().getRequest().getHeaders().getFirst(Correlation.USER_SUBJECT))
                .isEqualTo("alice");
        assertThat(routedExchange.get().getRequest().getHeaders().getFirst(Correlation.CUSTOMER_ID))
                .isEqualTo("cust-001");
    }

    private JwtAuthenticationToken jwtPrincipal() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                java.util.Map.of("alg", "none"),
                java.util.Map.of(
                        "sub", "alice-subject",
                        "preferred_username", "alice",
                        "customer_id", "cust-001"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private static class EmptyTracerProvider implements ObjectProvider<Tracer> {
        @Override
        public Tracer getObject(Object... args) throws BeansException {
            return null;
        }

        @Override
        public Tracer getIfAvailable() throws BeansException {
            return null;
        }

        @Override
        public Tracer getIfUnique() throws BeansException {
            return null;
        }

        @Override
        public Tracer getObject() throws BeansException {
            return null;
        }
    }
}
