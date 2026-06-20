package com.alradno.banking.payment;

import com.alradno.banking.common.money.CurrencyCode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountTransferClient {
    private final RestTemplate restTemplate;
    private final String accountServiceUrl;

    public AccountTransferClient(RestTemplateBuilder restTemplateBuilder, @Value("${banking.account-service-url}") String accountServiceUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.accountServiceUrl = accountServiceUrl;
    }

    public TransferResponse transfer(TransferRequest request) {
        return restTemplate.postForObject(accountServiceUrl + "/internal/accounts/transfer", request, TransferResponse.class);
    }

    public record TransferRequest(
            UUID sourceAccountId,
            UUID targetAccountId,
            UUID customerId,
            CurrencyCode currency,
            long amountMinor,
            String correlationId) {
    }

    public record TransferResponse(boolean approved, String reason, long sourceBalanceMinor, long targetBalanceMinor) {
    }
}
