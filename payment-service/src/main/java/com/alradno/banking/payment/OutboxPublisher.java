package com.alradno.banking.payment;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${banking.outbox.publisher-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception ex) {
                LOGGER.warn("outbox publish failed eventId={} eventType={}", event.getId(), event.getEventType(), ex);
            }
        }
    }
}
