package com.skyhigh.infrastructure.external;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

public interface PaymentServiceClient {

    PaymentResult processPayment(String paymentRef, BigDecimal amount);

    @Data
    @Builder
    class PaymentResult {
        private boolean success;
        private String transactionId;
        private String message;
    }
}
