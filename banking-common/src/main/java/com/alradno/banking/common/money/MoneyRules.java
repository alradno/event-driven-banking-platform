package com.alradno.banking.common.money;

public final class MoneyRules {
    private MoneyRules() {
    }

    public static void requirePositiveMinor(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
    }
}
