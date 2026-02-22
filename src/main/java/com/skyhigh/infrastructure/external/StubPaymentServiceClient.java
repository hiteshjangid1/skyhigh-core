package com.skyhigh.infrastructure.external;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@org.springframework.context.annotation.Primary
@ConditionalOnProperty(name = "skyhigh.payment.stub-enabled", havingValue = "true", matchIfMissing = true)
public class StubPaymentServiceClient implements PaymentServiceClient {

    @Override
    public PaymentResult processPayment(String paymentRef, BigDecimal amount) {
        return PaymentResult.builder()
                .success(true)
                .transactionId(UUID.randomUUID().toString())
                .message("Payment successful")
                .build();
    }
}
