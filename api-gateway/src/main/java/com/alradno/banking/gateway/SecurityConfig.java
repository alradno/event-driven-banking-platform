package com.alradno.banking.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(policy -> policy.policyDirectives("default-src 'self'")))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/prometheus").permitAll()
                        .pathMatchers(HttpMethod.GET, "/customers/me").hasAnyRole("CUSTOMER", "SUPPORT_AGENT", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/customers/**").hasAnyRole("SUPPORT_AGENT", "BACKOFFICE_OPERATOR", "ADMIN")
                        .pathMatchers("/accounts/**").hasAnyRole("CUSTOMER", "SUPPORT_AGENT", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/payments/**").hasAnyRole("CUSTOMER", "BACKOFFICE_OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/payments/**").hasAnyRole("CUSTOMER", "BACKOFFICE_OPERATOR", "ADMIN")
                        .pathMatchers(HttpMethod.GET, "/payments/**").hasAnyRole("CUSTOMER", "SUPPORT_AGENT", "ADMIN")
                        .pathMatchers("/audits/**").hasAnyRole("AUDITOR", "SRE", "ADMIN")
                        .pathMatchers("/ai/**").hasAnyRole("SRE", "ADMIN")
                        .pathMatchers("/demo/**").hasAnyRole("SRE", "ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
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
