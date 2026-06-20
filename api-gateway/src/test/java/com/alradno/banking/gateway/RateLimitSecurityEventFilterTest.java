package com.alradno.banking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RateLimitSecurityEventFilterTest {
    @Test
    void wrapsGatewayFiltersSoRateLimitRejectionsAreAudited() {
        CapturingSecurityEventPublisher publisher = new CapturingSecurityEventPublisher();
        RateLimitSecurityEventFilter filter = new RateLimitSecurityEventFilter(publisher);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/accounts"));

        StepVerifier.create(filter.filter(exchange, routed -> {
            routed.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return Mono.empty();
        })).verifyComplete();

        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(publisher.eventTypes).containsExactly(SecurityEventPublisher.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void ignoresNonRateLimitedResponses() {
        CapturingSecurityEventPublisher publisher = new CapturingSecurityEventPublisher();
        RateLimitSecurityEventFilter filter = new RateLimitSecurityEventFilter(publisher);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/accounts"));

        StepVerifier.create(filter.filter(exchange, routed -> {
            routed.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        })).verifyComplete();

        assertThat(publisher.eventTypes).isEmpty();
    }

    private static class CapturingSecurityEventPublisher extends SecurityEventPublisher {
        private final List<String> eventTypes = new ArrayList<>();

        CapturingSecurityEventPublisher() {
            super(null, null);
        }

        @Override
        public Mono<Void> publish(ServerWebExchange exchange, String eventType, String reason) {
            eventTypes.add(eventType);
            return Mono.empty();
        }
    }
}
