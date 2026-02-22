package com.skyhigh.interfaces.rest.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddBaggageRequest {
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;

    @NotBlank(message = "Baggage ID is required")
    private String baggageId;
}
