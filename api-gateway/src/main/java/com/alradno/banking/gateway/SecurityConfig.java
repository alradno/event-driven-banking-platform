package com.alradno.banking.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {
    static final String[] ACCOUNT_PATHS = {"/accounts", "/accounts/**"};
    static final String[] PAYMENT_PATHS = {"/payments", "/payments/**"};
    static final String[] AUDIT_PATHS = {"/audits", "/audits/**"};
    static final String[] AI_PATHS = {"/ai", "/ai/**"};
    static final String[] DEMO_PATHS = {"/demo", "/demo/**"};

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            SecurityEventPublisher securityEvents) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(policy -> policy.policyDirectives("default-src 'self'")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> reject(
                                exchange,
                                securityEvents,
                                HttpStatus.UNAUTHORIZED,
                                SecurityEventPublisher.LOGIN_FAILED,
                                "missing or invalid bearer token"))
                        .accessDeniedHandler((exchange, denied) -> reject(
                                exchange,
                                securityEvents,
                                HttpStatus.FORBIDDEN,
                                SecurityEventPublisher.ACCESS_DENIED,
                                "authenticated principal lacks required role or scope")))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/prometheus").permitAll()
                        .pathMatchers(HttpMethod.GET, "/customers/me").hasAnyRole("CUSTOMER", "SUPPORT_AGENT", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/customers/**").hasAnyRole("SUPPORT_AGENT", "BACKOFFICE_OPERATOR", "ADMIN")
                        .pathMatchers(ACCOUNT_PATHS).hasAnyRole("CUSTOMER", "SUPPORT_AGENT", "ADMIN")
                        .pathMatchers(HttpMethod.POST, PAYMENT_PATHS).hasAnyRole("CUSTOMER", "BACKOFFICE_OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, PAYMENT_PATHS).hasAnyRole("CUSTOMER", "BACKOFFICE_OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.GET, PAYMENT_PATHS).hasAnyRole("CUSTOMER", "SUPPORT_AGENT", "ADMIN")
                        .pathMatchers(AUDIT_PATHS).hasAnyRole("AUDITOR", "SRE", "ADMIN")
                        .pathMatchers(AI_PATHS).hasAnyRole("SRE", "ADMIN")
                        .pathMatchers(DEMO_PATHS).hasAnyRole("SRE", "ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private Mono<Void> reject(
            ServerWebExchange exchange,
            SecurityEventPublisher securityEvents,
            HttpStatus status,
            String eventType,
            String reason) {
        exchange.getResponse().setStatusCode(status);
        return securityEvents.publish(exchange, eventType, reason)
                .then(Mono.defer(() -> {
                    exchange.getResponse().setStatusCode(status);
                    return exchange.getResponse().setComplete();
                }));
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Flux<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesValue = realmAccess.get("roles");
            if (rolesValue instanceof Collection<?> roles) {
                roles.stream()
                        .map(Object::toString)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .forEach(authorities::add);
            }
        }
        Object scopes = jwt.getClaims().getOrDefault("scope", "");
        for (String scope : scopes.toString().split(" ")) {
            if (!scope.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
        }
        return Flux.fromIterable(authorities);
    }
}
