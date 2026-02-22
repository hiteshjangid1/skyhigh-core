package com.skyhigh.interfaces.rest.dto.seat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelSeatRequest {
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;
}
