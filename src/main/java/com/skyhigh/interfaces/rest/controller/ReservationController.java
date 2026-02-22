package com.skyhigh.interfaces.rest.controller;

import com.skyhigh.application.seat.SeatService;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import com.skyhigh.interfaces.rest.dto.seat.CancelSeatRequest;
import com.skyhigh.interfaces.rest.dto.seat.ConfirmSeatRequest;
import com.skyhigh.interfaces.rest.dto.seat.ReservationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final SeatService seatService;
    private final SeatReservationRepository reservationRepository;

    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ReservationDto> confirmReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody ConfirmSeatRequest request) {
        SeatReservation reservation = seatService.confirmSeat(reservationId, request.getPassengerId());
        return ResponseEntity.ok(ReservationDto.from(reservation));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody CancelSeatRequest request) {
        SeatReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        seatService.cancelSeat(reservationId, request.getPassengerId(), reservation.getFlightId());
        return ResponseEntity.noContent().build();
    }
}
