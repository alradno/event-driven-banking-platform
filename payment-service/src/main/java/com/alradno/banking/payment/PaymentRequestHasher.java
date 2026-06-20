package com.alradno.banking.payment;

import com.alradno.banking.payment.api.PaymentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PaymentRequestHasher {
    private PaymentRequestHasher() {
    }

    public static String hash(PaymentRequest request) {
        String canonical = request.sourceAccountId()
                + "|"
                + request.targetAccountId()
                + "|"
                + request.currency()
                + "|"
                + request.amountMinor()
                + "|"
                + nullToEmpty(request.description());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
