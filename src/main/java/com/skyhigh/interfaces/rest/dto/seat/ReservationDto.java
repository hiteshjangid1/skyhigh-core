package com.skyhigh.interfaces.rest.dto.seat;

import com.skyhigh.domain.seat.entity.SeatReservation;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ReservationDto {
    private Long id;
    private Long seatId;
    private Long flightId;
    private String passengerId;
    private String state;
    private Instant heldAt;
    private Instant heldUntil;
    private Instant confirmedAt;

    public static ReservationDto from(SeatReservation r) {
        return ReservationDto.builder()
                .id(r.getId())
                .seatId(r.getSeatId())
                .flightId(r.getFlightId())
                .passengerId(r.getPassengerId())
                .state(r.getState().name())
                .heldAt(r.getHeldAt())
                .heldUntil(r.getHeldUntil())
                .confirmedAt(r.getConfirmedAt())
                .build();
    }
}
