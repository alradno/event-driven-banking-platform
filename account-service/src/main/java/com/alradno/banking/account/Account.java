package com.alradno.banking.account;

import com.alradno.banking.common.money.CurrencyCode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private UUID id;
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;
    private long balanceMinor;
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    protected Account() {
    }

    public Account(UUID id, UUID customerId, CurrencyCode currency, long balanceMinor, AccountStatus status) {
        this.id = id;
        this.customerId = customerId;
        this.currency = currency;
        this.balanceMinor = balanceMinor;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void debit(long amountMinor) {
        balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        balanceMinor += amountMinor;
    }
}
