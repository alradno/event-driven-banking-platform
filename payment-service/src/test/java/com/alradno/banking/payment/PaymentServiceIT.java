package com.alradno.banking.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.alradno.banking.common.http.Correlation;
import com.alradno.banking.common.money.CurrencyCode;
import com.alradno.banking.payment.api.PaymentRequest;
import com.alradno.banking.payment.api.PaymentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.kafka.bootstrap-servers=localhost:1",
            "banking.outbox.publisher-delay-ms=600000",
            "management.tracing.enabled=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class PaymentServiceIT {
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID TARGET_ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final WireMockServer accountService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        accountService.start();
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("banking.account-service-url", accountService::baseUrl);
    }

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        payments.deleteAll();
    }

    @AfterEach
    void resetWireMock() {
        accountService.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        accountService.stop();
    }

    @Test
    void completesPaymentAgainstPostgresAndWireMockedAccountService() throws Exception {
        accountService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/accounts/transfer"))
                .withRequestBody(equalToJson("""
                        {
                          "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
                          "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
                          "customerId": "11111111-1111-1111-1111-111111111111",
                          "currency": "CHF",
                          "amountMinor": 321,
                          "correlationId": "it-correlation-completed"
                        }
                        """))
                .willReturn(okJson("""
                        {"approved":true,"reason":"APPROVED","sourceBalanceMinor":679,"targetBalanceMinor":1321}
                        """)));

        ResponseEntity<PaymentResponse> response = postPayment(
                "it-key-completed",
                "it-correlation-completed",
                "it-trace-completed",
                new PaymentRequest(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, CurrencyCode.CHF, 321, "it completed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.getBody().correlationId()).isEqualTo("it-correlation-completed");
        accountService.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/transfer")));
        assertThat(payments.findAll()).hasSize(1);

        List<OutboxEvent> events = outbox.findAll();
        assertThat(events)
                .extracting(OutboxEvent::getEventType)
                .contains("payment.created", "payment.validated", "payment.processing", "payment.completed");

        JsonNode completed = payloadFor(events, "payment.completed");
        assertThat(completed.path("correlationId").asText()).isEqualTo("it-correlation-completed");
        assertThat(completed.path("traceId").asText()).isEqualTo("it-trace-completed");
        assertThat(completed.path("payload").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void failsPaymentWhenDownstreamAccountServiceIsUnavailable() throws Exception {
        accountService.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/internal/accounts/transfer"))
                .willReturn(serviceUnavailable()));

        ResponseEntity<PaymentResponse> response = postPayment(
                "it-key-failed",
                "it-correlation-failed",
                "it-trace-failed",
                new PaymentRequest(SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID, CurrencyCode.CHF, 100, "it failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getBody().rejectionReason()).isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
        accountService.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/transfer")));

        JsonNode failed = payloadFor(outbox.findAll(), "payment.failed");
        assertThat(failed.path("payload").path("reason").asText()).isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
    }

    private ResponseEntity<PaymentResponse> postPayment(
            String idempotencyKey,
            String correlationId,
            String traceId,
            PaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(Correlation.CUSTOMER_ID, CUSTOMER_ID.toString());
        headers.set(Correlation.CORRELATION_ID, correlationId);
        headers.set(Correlation.TRACE_ID, traceId);
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.postForEntity(
                "http://localhost:" + port + "/payments",
                new HttpEntity<>(request, headers),
                PaymentResponse.class);
    }

    private JsonNode payloadFor(List<OutboxEvent> events, String eventType) throws Exception {
        OutboxEvent event = events.stream()
                .filter(candidate -> eventType.equals(candidate.getEventType()))
                .findFirst()
                .orElseThrow();
        return objectMapper.readTree(event.getPayload());
    }
}
