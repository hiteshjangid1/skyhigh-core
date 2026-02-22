package com.skyhigh.interfaces.rest.controller;

import com.skyhigh.application.seat.SeatService;
import com.skyhigh.domain.seat.entity.Seat;
import com.skyhigh.interfaces.rest.dto.seat.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/flights/{flightId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSeatMap(@PathVariable Long flightId) {
        List<Seat> seats = seatService.getSeatsByFlight(flightId);
        List<SeatDto> seatDtos = seats.stream()
                .map(SeatDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
                "flightId", flightId,
                "seats", seatDtos
        ));
    }

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<ReservationDto> holdSeat(
            @PathVariable Long flightId,
            @PathVariable Long seatId,
            @Valid @RequestBody HoldSeatRequest request) {
        var reservation = seatService.holdSeat(flightId, seatId, request.getPassengerId());
        return ResponseEntity.ok(ReservationDto.from(reservation));
    }
}
