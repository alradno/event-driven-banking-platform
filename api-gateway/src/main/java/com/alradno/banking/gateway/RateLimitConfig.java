package com.alradno.banking.gateway;

import java.security.Principal;
import java.util.Objects;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    @Bean
    KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    if (exchange.getRequest().getRemoteAddress() == null) {
                        return "anonymous";
                    }
                    return Objects.toString(exchange.getRequest().getRemoteAddress().getAddress(), "anonymous");
                }));
    }
}
