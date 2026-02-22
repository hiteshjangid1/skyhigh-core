package com.skyhigh.interfaces.rest.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompletePaymentRequest {
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;

    @NotBlank(message = "Payment reference is required")
    private String paymentRef;
}
