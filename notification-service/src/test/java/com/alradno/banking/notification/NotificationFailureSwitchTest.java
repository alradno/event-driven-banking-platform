package com.alradno.banking.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotificationFailureSwitchTest {
    @Test
    void canToggleDemoFailures() {
        NotificationFailureSwitch failureSwitch = new NotificationFailureSwitch(false);
        assertFalse(failureSwitch.enabled());
        failureSwitch.setEnabled(true);
        assertTrue(failureSwitch.enabled());
    }
}
