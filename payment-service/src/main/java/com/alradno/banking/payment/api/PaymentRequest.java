package com.alradno.banking.payment.api;

import com.alradno.banking.common.money.CurrencyCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID targetAccountId,
        @NotNull CurrencyCode currency,
        @Min(1) long amountMinor,
        String description) {
}
