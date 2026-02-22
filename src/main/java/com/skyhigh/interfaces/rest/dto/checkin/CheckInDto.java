package com.skyhigh.interfaces.rest.dto.checkin;

import com.skyhigh.domain.checkin.entity.CheckIn;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CheckInDto {
    private Long id;
    private Long flightId;
    private String passengerId;
    private Long reservationId;
    private String status;
    private Instant createdAt;
    private Instant completedAt;

    public static CheckInDto from(CheckIn c) {
        return CheckInDto.builder()
                .id(c.getId())
                .flightId(c.getFlightId())
                .passengerId(c.getPassengerId())
                .reservationId(c.getReservationId())
                .status(c.getStatus().name())
                .createdAt(c.getCreatedAt())
                .completedAt(c.getCompletedAt())
                .build();
    }
}
