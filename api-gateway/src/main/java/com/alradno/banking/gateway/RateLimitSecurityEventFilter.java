package com.alradno.banking.gateway;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RateLimitSecurityEventFilter implements WebFilter, Ordered {
    private final SecurityEventPublisher securityEvents;

    public RateLimitSecurityEventFilter(SecurityEventPublisher securityEvents) {
        this.securityEvents = securityEvents;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).then(Mono.defer(() -> {
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return securityEvents.publish(
                        exchange,
                        SecurityEventPublisher.RATE_LIMIT_EXCEEDED,
                        "gateway rate limit exceeded");
            }
            return Mono.empty();
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
