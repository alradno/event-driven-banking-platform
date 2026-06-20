package com.alradno.banking.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alradno.banking.common.money.CurrencyCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalTransferRulesTest {
    @Test
    void accountReportsActiveOnlyForActiveStatus() {
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID(), CurrencyCode.CHF, 1_000, AccountStatus.FROZEN);
        assertFalse(account.isActive());
    }

    @Test
    void debitAndCreditUseMinorUnits() {
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID(), CurrencyCode.CHF, 1_000, AccountStatus.ACTIVE);
        account.debit(125);
        account.credit(25);
        assertTrue(account.getBalanceMinor() == 900);
    }
}
