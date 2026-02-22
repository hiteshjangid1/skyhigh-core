package com.skyhigh.interfaces.rest.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartCheckInRequest {
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;

    @NotNull(message = "Seat ID is required")
    private Long seatId;
}
