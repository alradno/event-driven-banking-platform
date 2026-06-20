package com.alradno.banking.common.http;

import java.time.Instant;

public record StandardError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId,
        String traceId,
        String path) {

    public static StandardError of(
            int status,
            String code,
            String message,
            String correlationId,
            String traceId,
            String path) {
        return new StandardError(Instant.now(), status, code, message, correlationId, traceId, path);
    }
}
