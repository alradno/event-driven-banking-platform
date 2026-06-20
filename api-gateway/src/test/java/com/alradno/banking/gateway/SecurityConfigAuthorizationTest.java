package com.alradno.banking.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClientConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@SpringBootTest(
        classes = {ApiGatewayApplication.class, SecurityConfigAuthorizationTest.TestRoutes.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
class SecurityConfigAuthorizationTest {
    @Autowired
    WebTestClient webTestClient;

    @Test
    void sreCannotCreatePaymentOnBasePath() {
        webTestClient
                .mutateWith(jwtWithRoles("SRE"))
                .post()
                .uri("/payments")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void customerCanCreatePaymentOnBasePath() {
        webTestClient
                .mutateWith(jwtWithRoles("CUSTOMER"))
                .post()
                .uri("/payments")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void sreCannotReadAccountsOnBasePath() {
        webTestClient
                .mutateWith(jwtWithRoles("SRE"))
                .get()
                .uri("/accounts")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    private static WebTestClientConfigurer jwtWithRoles(String... roles) {
        List<GrantedAuthority> authorities = List.of(roles).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return mockJwt().authorities(authorities);
    }

    @TestConfiguration
    static class TestRoutes {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.error(new UnsupportedOperationException("Use mockJwt in security tests"));
        }

        @Bean
        @Primary
        SecurityEventPublisher securityEventPublisher() {
            return new SecurityEventPublisher(null, null) {
                @Override
                public Mono<Void> publish(ServerWebExchange exchange, String eventType, String reason) {
                    return Mono.empty();
                }
            };
        }

        @RestController
        static class Controller {
            @PostMapping("/payments")
            Mono<String> createPayment() {
                return Mono.just("ok");
            }

            @GetMapping("/accounts")
            Mono<String> listAccounts() {
                return Mono.just("ok");
            }
        }
    }
}
