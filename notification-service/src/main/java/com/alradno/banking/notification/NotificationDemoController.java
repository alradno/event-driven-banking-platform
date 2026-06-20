package com.alradno.banking.notification;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class NotificationDemoController {
    private final NotificationFailureSwitch failureSwitch;

    public NotificationDemoController(NotificationFailureSwitch failureSwitch) {
        this.failureSwitch = failureSwitch;
    }

    @PostMapping("/notification-failure")
    public Map<String, Boolean> setFailure(@RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        failureSwitch.setEnabled(enabled);
        return Map.of("enabled", failureSwitch.enabled());
    }
}
