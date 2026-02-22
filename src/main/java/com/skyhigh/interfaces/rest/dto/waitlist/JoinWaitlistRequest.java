package com.skyhigh.interfaces.rest.dto.waitlist;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinWaitlistRequest {
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;
}
