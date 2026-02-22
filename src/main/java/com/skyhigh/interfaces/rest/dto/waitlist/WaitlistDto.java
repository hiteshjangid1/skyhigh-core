package com.skyhigh.interfaces.rest.dto.waitlist;

import com.skyhigh.domain.waitlist.entity.WaitlistEntry;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class WaitlistDto {
    private Long id;
    private Long flightId;
    private String passengerId;
    private String status;
    private Long reservationId;
    private Instant createdAt;
    private Instant assignedAt;

    public static WaitlistDto from(WaitlistEntry e) {
        return WaitlistDto.builder()
                .id(e.getId())
                .flightId(e.getFlightId())
                .passengerId(e.getPassengerId())
                .status(e.getStatus().name())
                .reservationId(e.getReservationId())
                .createdAt(e.getCreatedAt())
                .assignedAt(e.getAssignedAt())
                .build();
    }
}
