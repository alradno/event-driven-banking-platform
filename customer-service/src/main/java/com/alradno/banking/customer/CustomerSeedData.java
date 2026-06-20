package com.alradno.banking.customer;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CustomerSeedData {
    static final UUID ALICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Bean
    CommandLineRunner seedCustomers(CustomerRepository customers) {
        return args -> {
            if (customers.count() == 0) {
                customers.save(new Customer(ALICE_ID, "alice", "Alice Martin", CustomerStatus.ACTIVE, RiskLevel.LOW));
                customers.save(new Customer(BOB_ID, "bob", "Bob Keller", CustomerStatus.ACTIVE, RiskLevel.MEDIUM));
            }
        };
    }
}
