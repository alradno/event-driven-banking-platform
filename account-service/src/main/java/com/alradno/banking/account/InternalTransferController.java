package com.alradno.banking.account;

import com.alradno.banking.common.money.CurrencyCode;
import com.alradno.banking.common.money.MoneyRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalTransferController {
    private final AccountRepository accounts;

    public InternalTransferController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/transfer")
    @Transactional
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        MoneyRules.requirePositiveMinor(request.amountMinor());
        List<Account> locked = accounts.findAllByIdIn(List.of(request.sourceAccountId(), request.targetAccountId()));
        Map<UUID, Account> byId = locked.stream().collect(Collectors.toMap(Account::getId, Function.identity()));
        Account source = byId.get(request.sourceAccountId());
        Account target = byId.get(request.targetAccountId());

        if (source == null || target == null) {
            return TransferResponse.rejected("ACCOUNT_NOT_FOUND");
        }
        if (!source.getCustomerId().equals(request.customerId())) {
            return TransferResponse.rejected("OWNERSHIP_MISMATCH");
        }
        if (!source.isActive() || !target.isActive()) {
            return TransferResponse.rejected("ACCOUNT_NOT_ACTIVE");
        }
        if (source.getCurrency() != request.currency() || target.getCurrency() != request.currency()) {
            return TransferResponse.rejected("CURRENCY_MISMATCH");
        }
        if (source.getBalanceMinor() < request.amountMinor()) {
            return TransferResponse.rejected("INSUFFICIENT_FUNDS");
        }

        source.debit(request.amountMinor());
        target.credit(request.amountMinor());
        accounts.save(source);
        accounts.save(target);
        return new TransferResponse(true, "APPROVED", source.getBalanceMinor(), target.getBalanceMinor());
    }

    public record TransferRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID targetAccountId,
            @NotNull UUID customerId,
            @NotNull CurrencyCode currency,
            @Min(1) long amountMinor,
            String correlationId) {
    }

    public record TransferResponse(boolean approved, String reason, long sourceBalanceMinor, long targetBalanceMinor) {
        static TransferResponse rejected(String reason) {
            return new TransferResponse(false, reason, 0, 0);
        }
    }
}
