package com.skyhigh.interfaces.rest.controller;

import com.skyhigh.application.checkin.CheckInService;
import com.skyhigh.domain.checkin.entity.CheckIn;
import com.skyhigh.interfaces.rest.dto.checkin.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/start")
    public ResponseEntity<CheckInDto> startCheckIn(
            @RequestParam Long flightId,
            @Valid @RequestBody StartCheckInRequest request) {
        CheckIn checkIn = checkInService.startCheckIn(
                flightId,
                request.getPassengerId(),
                request.getSeatId());
        return ResponseEntity.ok(CheckInDto.from(checkIn));
    }

    @PostMapping("/{checkInId}/baggage")
    public ResponseEntity<CheckInDto> addBaggage(
            @PathVariable Long checkInId,
            @Valid @RequestBody AddBaggageRequest request) {
        CheckIn checkIn = checkInService.addBaggage(
                checkInId,
                request.getBaggageId(),
                request.getPassengerId());
        return ResponseEntity.ok(CheckInDto.from(checkIn));
    }

    @PostMapping("/{checkInId}/payment")
    public ResponseEntity<CheckInDto> completePayment(
            @PathVariable Long checkInId,
            @Valid @RequestBody CompletePaymentRequest request) {
        CheckIn checkIn = checkInService.completePayment(
                checkInId,
                request.getPaymentRef(),
                request.getPassengerId());
        return ResponseEntity.ok(CheckInDto.from(checkIn));
    }

    @GetMapping("/{checkInId}")
    public ResponseEntity<CheckInDto> getCheckIn(
            @PathVariable Long checkInId,
            @RequestParam String passengerId) {
        CheckIn checkIn = checkInService.getCheckIn(checkInId, passengerId);
        return ResponseEntity.ok(CheckInDto.from(checkIn));
    }
}
