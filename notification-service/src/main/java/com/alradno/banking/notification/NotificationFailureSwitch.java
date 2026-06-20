package com.alradno.banking.notification;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationFailureSwitch {
    private final AtomicBoolean enabled;

    public NotificationFailureSwitch(@Value("${banking.demo.notification-failure:false}") boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    public boolean enabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
