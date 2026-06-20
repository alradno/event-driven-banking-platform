package com.alradno.banking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.alradno.banking.common.events.EventEnvelope;
import com.alradno.banking.notification.NotificationDlqReplayService.ReplayResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.listener.missing-topics-fatal=false",
    "management.tracing.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class NotificationKafkaIT {
    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    NotificationDlqReplayService replayService;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void forwardsCompletedPaymentEventsToNotificationTopic() throws Exception {
        EventEnvelope envelope = event("payment.completed", "it-notification-forward");
        String raw = objectMapper.writeValueAsString(envelope);

        kafkaTemplate.send(NotificationDlqReplayService.PAYMENT_TOPIC, envelope.subject(), raw).get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();

        EventEnvelope forwarded = consumeEnvelope("bank.notification.events", "it-notification-forward");
        assertThat(forwarded.eventType()).isEqualTo("payment.completed");
        assertThat(forwarded.traceId()).isEqualTo("trace-it-notification-forward");
    }

    @Test
    void replaysOnlyMatchingDlqEnvelopeToPaymentTopic() throws Exception {
        EventEnvelope matching = event("payment.completed", "it-dlq-replay");
        EventEnvelope other = event("payment.completed", "it-dlq-other");
        kafkaTemplate.send(NotificationDlqReplayService.DLQ_TOPIC, matching.subject(), objectMapper.writeValueAsString(matching))
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(NotificationDlqReplayService.DLQ_TOPIC, other.subject(), objectMapper.writeValueAsString(other))
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();

        ReplayResult result = replayService.replayByCorrelationId("it-dlq-replay", 5);

        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.scanned()).isGreaterThanOrEqualTo(2);
        EventEnvelope replayed = consumeEnvelope(NotificationDlqReplayService.PAYMENT_TOPIC, "it-dlq-replay");
        assertThat(replayed.eventType()).isEqualTo("payment.completed");
        assertThat(replayService.replayByCorrelationId("it-dlq-missing", 5).replayed()).isZero();
    }

    private EventEnvelope event(String eventType, String correlationId) {
        return EventEnvelope.of(
                eventType,
                1,
                "payment-service",
                correlationId,
                "payment/" + correlationId,
                "trace-" + correlationId,
                "payment/" + correlationId,
                Map.of(
                        "paymentId", UUID.nameUUIDFromBytes(correlationId.getBytes()).toString(),
                        "status", eventType.substring(eventType.indexOf('.') + 1).toUpperCase()));
    }

    private EventEnvelope consumeEnvelope(String topic, String correlationId) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250));
                for (var record : records) {
                    EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
                    if (correlationId.equals(envelope.correlationId())) {
                        return envelope;
                    }
                }
            }
        }
        throw new AssertionError("Timed out waiting for correlationId " + correlationId + " on " + topic);
    }
}
