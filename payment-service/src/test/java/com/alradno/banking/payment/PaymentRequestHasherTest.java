package com.alradno.banking.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.alradno.banking.common.money.CurrencyCode;
import com.alradno.banking.payment.api.PaymentRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRequestHasherTest {
    @Test
    void duplicateBodiesProduceTheSameHashForIdempotency() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        PaymentRequest first = new PaymentRequest(source, target, CurrencyCode.CHF, 1250, "rent");
        PaymentRequest second = new PaymentRequest(source, target, CurrencyCode.CHF, 1250, "rent");

        assertEquals(PaymentRequestHasher.hash(first), PaymentRequestHasher.hash(second));
    }

    @Test
    void differentBodiesCannotReuseTheSameIdempotencyRecord() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        PaymentRequest first = new PaymentRequest(source, target, CurrencyCode.CHF, 1250, "rent");
        PaymentRequest second = new PaymentRequest(source, target, CurrencyCode.CHF, 1251, "rent");

        assertNotEquals(PaymentRequestHasher.hash(first), PaymentRequestHasher.hash(second));
    }
}
