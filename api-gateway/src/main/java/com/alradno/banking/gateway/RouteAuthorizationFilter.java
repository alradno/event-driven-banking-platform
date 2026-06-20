package com.alradno.banking.gateway;

import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RouteAuthorizationFilter implements WebFilter, Ordered {
    private static final String[] CUSTOMER_SELF_READ = {"CUSTOMER", "SUPPORT_AGENT", "ADMIN"};
    private static final String[] CUSTOMER_ANY_READ = {"SUPPORT_AGENT", "BACKOFFICE_OPERATOR", "ADMIN"};
    private static final String[] ACCOUNT_ACCESS = {"CUSTOMER", "SUPPORT_AGENT", "ADMIN"};
    private static final String[] PAYMENT_WRITE = {"CUSTOMER", "BACKOFFICE_OPERATOR", "ADMIN"};
    private static final String[] PAYMENT_READ = {"CUSTOMER", "SUPPORT_AGENT", "ADMIN"};
    private static final String[] AUDIT_ACCESS = {"AUDITOR", "SRE", "ADMIN"};
    private static final String[] OPERATOR_ACCESS = {"SRE", "ADMIN"};

    private final SecurityEventPublisher securityEvents;

    public RouteAuthorizationFilter(SecurityEventPublisher securityEvents) {
        this.securityEvents = securityEvents;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String[] requiredRoles = requiredRoles(exchange);
        if (requiredRoles == null) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(principal -> principal
                        .map(value -> authorize(exchange, chain, value, requiredRoles))
                        .orElseGet(() -> unauthorized(exchange)));
    }

    private Mono<Void> authorize(
            ServerWebExchange exchange,
            WebFilterChain chain,
            Principal principal,
            String[] requiredRoles) {
        if (principal instanceof Authentication authentication && hasAnyRole(authentication, requiredRoles)) {
            return chain.filter(exchange);
        }
        return reject(
                exchange,
                HttpStatus.FORBIDDEN,
                SecurityEventPublisher.ACCESS_DENIED,
                "authenticated principal lacks required route role");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return reject(
                exchange,
                HttpStatus.UNAUTHORIZED,
                SecurityEventPublisher.LOGIN_FAILED,
                "missing or invalid bearer token");
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String eventType, String reason) {
        exchange.getResponse().setStatusCode(status);
        return securityEvents.publish(exchange, eventType, reason)
                .then(Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(status);
                    return exchange.getResponse().setComplete();
                }));
    }

    private boolean hasAnyRole(Authentication authentication, String[] roles) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return Arrays.stream(roles)
                .map(role -> "ROLE_" + role)
                .anyMatch(authorities::contains);
    }

    private String[] requiredRoles(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (method == HttpMethod.GET && path.equals("/customers/me")) {
            return CUSTOMER_SELF_READ;
        }
        if (method == HttpMethod.GET && under(path, "/customers")) {
            return CUSTOMER_ANY_READ;
        }
        if (under(path, "/accounts")) {
            return ACCOUNT_ACCESS;
        }
        if (under(path, "/payments")) {
            if (method == HttpMethod.POST || method == HttpMethod.DELETE) {
                return PAYMENT_WRITE;
            }
            if (method == HttpMethod.GET) {
                return PAYMENT_READ;
            }
        }
        if (under(path, "/audits")) {
            return AUDIT_ACCESS;
        }
        if (under(path, "/ai") || under(path, "/demo")) {
            return OPERATOR_ACCESS;
        }
        return null;
    }

    private boolean under(String path, String basePath) {
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
