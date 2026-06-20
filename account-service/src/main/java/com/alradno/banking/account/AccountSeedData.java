package com.alradno.banking.account;

import com.alradno.banking.common.money.CurrencyCode;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AccountSeedData {
    static final UUID ALICE_CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BOB_CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Bean
    CommandLineRunner seedAccounts(AccountRepository accounts) {
        return args -> {
            if (accounts.count() == 0) {
                accounts.save(new Account(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"),
                        ALICE_CUSTOMER_ID,
                        CurrencyCode.CHF,
                        250_000,
                        AccountStatus.ACTIVE));
                accounts.save(new Account(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2"),
                        ALICE_CUSTOMER_ID,
                        CurrencyCode.EUR,
                        180_000,
                        AccountStatus.FROZEN));
                accounts.save(new Account(
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1"),
                        BOB_CUSTOMER_ID,
                        CurrencyCode.CHF,
                        75_000,
                        AccountStatus.ACTIVE));
            }
        };
    }
}
