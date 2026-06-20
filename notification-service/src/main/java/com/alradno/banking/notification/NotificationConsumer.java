package com.alradno.banking.notification;

import com.alradno.banking.common.events.EventCompatibility;
import com.alradno.banking.common.events.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationConsumer.class);
    private final NotificationFailureSwitch failureSwitch;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(
            NotificationFailureSwitch failureSwitch,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.failureSwitch = failureSwitch;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = "notification-service", topics = "bank.payment.events")
    public void onPaymentEvent(String raw) throws Exception {
        EventEnvelope event = objectMapper.readValue(raw, EventEnvelope.class);
        EventCompatibility.requireSupported(event);
        if (!event.eventType().equals("payment.completed") && !event.eventType().equals("payment.rejected")) {
            return;
        }
        if (failureSwitch.enabled()) {
            throw new IllegalStateException("demo notification failure enabled");
        }
        LOGGER.info(
                "notification prepared eventId={} eventType={} correlationId={} traceId={}",
                event.eventId(),
                event.eventType(),
                event.correlationId(),
                event.traceId());
        kafkaTemplate.send("bank.notification.events", event.subject(), raw);
    }
}
