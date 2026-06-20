package com.alradno.banking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import reactor.test.StepVerifier;

class SecurityPathMatcherTest {
    @ParameterizedTest
    @MethodSource("securedMethodPaths")
    void methodSpecificRulesCoverBaseCollectionPaths(HttpMethod method, String[] patterns, String path) {
        assertMatches(pathMatchers(method, patterns), method, path);
    }

    @ParameterizedTest
    @MethodSource("securedPaths")
    void roleRulesCoverBaseCollectionPaths(String[] patterns, String path) {
        assertMatches(pathMatchers(patterns), HttpMethod.GET, path);
    }

    static Stream<Arguments> securedMethodPaths() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, SecurityConfig.PAYMENT_PATHS, "/payments"),
                Arguments.of(HttpMethod.POST, SecurityConfig.PAYMENT_PATHS, "/payments/123"),
                Arguments.of(HttpMethod.DELETE, SecurityConfig.PAYMENT_PATHS, "/payments"),
                Arguments.of(HttpMethod.GET, SecurityConfig.PAYMENT_PATHS, "/payments"));
    }

    static Stream<Arguments> securedPaths() {
        return Stream.of(
                Arguments.of(SecurityConfig.ACCOUNT_PATHS, "/accounts"),
                Arguments.of(SecurityConfig.ACCOUNT_PATHS, "/accounts/123"),
                Arguments.of(SecurityConfig.AUDIT_PATHS, "/audits"),
                Arguments.of(SecurityConfig.AUDIT_PATHS, "/audits/123"),
                Arguments.of(SecurityConfig.AI_PATHS, "/ai"),
                Arguments.of(SecurityConfig.AI_PATHS, "/ai/incidents/analyze"),
                Arguments.of(SecurityConfig.DEMO_PATHS, "/demo"),
                Arguments.of(SecurityConfig.DEMO_PATHS, "/demo/notification-failure"));
    }

    private static void assertMatches(ServerWebExchangeMatcher matcher, HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path).build());

        StepVerifier.create(matcher.matches(exchange))
                .assertNext(result -> assertThat(result.isMatch()).isTrue())
                .verifyComplete();
    }
}
