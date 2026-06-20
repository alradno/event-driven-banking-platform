package com.alradno.banking.account;

import com.alradno.banking.common.http.Correlation;
import com.alradno.banking.common.money.CurrencyCode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AccountResponse> mine(@RequestHeader(Correlation.CUSTOMER_ID) UUID customerId) {
        return accounts.findByCustomerId(customerId).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id, @RequestHeader(Correlation.CUSTOMER_ID) UUID customerId) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"));
        if (!account.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account owner mismatch");
        }
        return AccountResponse.from(account);
    }

    public record AccountResponse(UUID id, UUID customerId, CurrencyCode currency, long balanceMinor, AccountStatus status) {
        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getCustomerId(),
                    account.getCurrency(),
                    account.getBalanceMinor(),
                    account.getStatus());
        }
    }
}
