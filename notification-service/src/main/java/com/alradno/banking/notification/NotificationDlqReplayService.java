package com.alradno.banking.notification;

import com.alradno.banking.common.events.EventCompatibility;
import com.alradno.banking.common.events.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationDlqReplayService {
    static final String PAYMENT_TOPIC = "bank.payment.events";
    static final String DLQ_TOPIC = "bank.payment.events.notification-service.dlq";

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NotificationDlqReplayService(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public ReplayResult replayByCorrelationId(String correlationId, int maxRecords) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        int limit = Math.max(1, Math.min(maxRecords, 25));
        int scanned = 0;
        int replayed = 0;

        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "notification-dlq-replay-" + UUID.randomUUID(),
                "notification-dlq-replay")) {
            List<PartitionInfo> partitions = consumer.partitionsFor(DLQ_TOPIC);
            if (partitions == null || partitions.isEmpty()) {
                return new ReplayResult(DLQ_TOPIC, PAYMENT_TOPIC, correlationId, 0, 0);
            }

            List<TopicPartition> topicPartitions = partitions.stream()
                    .map(partition -> new TopicPartition(DLQ_TOPIC, partition.partition()))
                    .toList();
            consumer.assign(topicPartitions);
            consumer.seekToBeginning(topicPartitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);

            while (replayed < limit && hasRemainingRecords(consumer, topicPartitions, endOffsets)) {
                var records = consumer.poll(Duration.ofMillis(500));
                scanned += records.count();
                for (var record : records) {
                    if (replayed >= limit) {
                        break;
                    }
                    if (matchesCorrelationId(record.value(), correlationId)) {
                        EventEnvelope event = objectMapper.readValue(record.value(), EventEnvelope.class);
                        EventCompatibility.requireSupported(event);
                        kafkaTemplate.send(PAYMENT_TOPIC, event.subject(), record.value()).get(5, TimeUnit.SECONDS);
                        replayed++;
                    }
                }
            }
            kafkaTemplate.flush();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to replay notification DLQ", ex);
        }

        return new ReplayResult(DLQ_TOPIC, PAYMENT_TOPIC, correlationId, scanned, replayed);
    }

    boolean matchesCorrelationId(String raw, String correlationId) {
        try {
            EventEnvelope event = objectMapper.readValue(raw, EventEnvelope.class);
            EventCompatibility.requireSupported(event);
            return correlationId.equals(event.correlationId());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasRemainingRecords(
            Consumer<String, String> consumer,
            List<TopicPartition> topicPartitions,
            Map<TopicPartition, Long> endOffsets) {
        return topicPartitions.stream()
                .anyMatch(partition -> consumer.position(partition) < endOffsets.getOrDefault(partition, 0L));
    }

    public record ReplayResult(
            String dlqTopic,
            String replayTopic,
            String correlationId,
            int scanned,
            int replayed) {
    }
}
