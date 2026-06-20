package com.alradno.banking.common.http;

import java.util.UUID;

public final class Correlation {
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String USER_SUBJECT = "X-User-Subject";
    public static final String CUSTOMER_ID = "X-Customer-Id";

    private Correlation() {
    }

    public static String presentOrNew(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
