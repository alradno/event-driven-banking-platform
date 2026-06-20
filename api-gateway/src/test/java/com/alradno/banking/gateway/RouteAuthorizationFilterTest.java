package com.alradno.banking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RouteAuthorizationFilterTest {
    @Test
    void sreCannotCreatePaymentOnBasePath() {
        CapturingSecurityEventPublisher publisher = new CapturingSecurityEventPublisher();
        RouteAuthorizationFilter filter = new RouteAuthorizationFilter(publisher);
        AtomicBoolean reachedBackend = new AtomicBoolean(false);
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/payments", auth("sre", "ROLE_SRE"));

        StepVerifier.create(filter.filter(exchange, chain(reachedBackend))).verifyComplete();

        assertThat(reachedBackend.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publisher.eventTypes).containsExactly(SecurityEventPublisher.ACCESS_DENIED);
    }

    @Test
    void customerCanCreatePaymentOnBasePath() {
        CapturingSecurityEventPublisher publisher = new CapturingSecurityEventPublisher();
        RouteAuthorizationFilter filter = new RouteAuthorizationFilter(publisher);
        AtomicBoolean reachedBackend = new AtomicBoolean(false);
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/payments", auth("alice", "ROLE_CUSTOMER"));

        StepVerifier.create(filter.filter(exchange, chain(reachedBackend))).verifyComplete();

        assertThat(reachedBackend.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(publisher.eventTypes).isEmpty();
    }

    @Test
    void protectedRoutesWithoutPrincipalReturnUnauthorized() {
        CapturingSecurityEventPublisher publisher = new CapturingSecurityEventPublisher();
        RouteAuthorizationFilter filter = new RouteAuthorizationFilter(publisher);
        AtomicBoolean reachedBackend = new AtomicBoolean(false);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/accounts"));

        StepVerifier.create(filter.filter(exchange, chain(reachedBackend))).verifyComplete();

        assertThat(reachedBackend.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(publisher.eventTypes).containsExactly(SecurityEventPublisher.LOGIN_FAILED);
    }

    private ServerWebExchange exchange(HttpMethod method, String path, TestingAuthenticationToken authentication) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        return exchange.mutate().principal(Mono.just(authentication)).build();
    }

    private TestingAuthenticationToken auth(String name, String... authorities) {
        return new TestingAuthenticationToken(name, "n/a", authorities);
    }

    private WebFilterChain chain(AtomicBoolean reachedBackend) {
        return exchange -> {
            reachedBackend.set(true);
            return Mono.empty();
        };
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
