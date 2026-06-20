package com.alradno.banking.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RateLimitConfigTest {
    @Test
    void exposesKeyResolver() {
        assertNotNull(new RateLimitConfig().principalOrIpKeyResolver());
    }
}
