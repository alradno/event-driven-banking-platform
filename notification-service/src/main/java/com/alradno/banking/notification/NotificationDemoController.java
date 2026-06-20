package com.alradno.banking.notification;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/demo")
public class NotificationDemoController {
    private final NotificationFailureSwitch failureSwitch;
    private final NotificationDlqReplayService dlqReplayService;

    public NotificationDemoController(
            NotificationFailureSwitch failureSwitch,
            NotificationDlqReplayService dlqReplayService) {
        this.failureSwitch = failureSwitch;
        this.dlqReplayService = dlqReplayService;
    }

    @PostMapping("/notification-failure")
    public Map<String, Boolean> setFailure(@RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        failureSwitch.setEnabled(enabled);
        return Map.of("enabled", failureSwitch.enabled());
    }

    @PostMapping("/notification-dlq/replay")
    public NotificationDlqReplayService.ReplayResult replayDlq(@RequestBody Map<String, Object> body) {
        String correlationId = stringValue(body.get("correlationId"));
        if (correlationId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "correlationId is required");
        }
        return dlqReplayService.replayByCorrelationId(correlationId, maxRecords(body.getOrDefault("maxRecords", 1)));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private int maxRecords(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRecords must be numeric", ex);
        }
    }
}
