package com.skyhigh.interfaces.rest.controller;

import com.skyhigh.application.waitlist.WaitlistService;
import com.skyhigh.domain.waitlist.entity.WaitlistEntry;
import com.skyhigh.interfaces.rest.dto.waitlist.JoinWaitlistRequest;
import com.skyhigh.interfaces.rest.dto.waitlist.WaitlistDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/flights/{flightId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<WaitlistDto> joinWaitlist(
            @PathVariable Long flightId,
            @Valid @RequestBody JoinWaitlistRequest request) {
        WaitlistEntry entry = waitlistService.joinWaitlist(flightId, request.getPassengerId());
        return ResponseEntity.ok(WaitlistDto.from(entry));
    }

    @GetMapping
    public ResponseEntity<WaitlistDto> getStatus(
            @PathVariable Long flightId,
            @RequestParam String passengerId) {
        WaitlistEntry entry = waitlistService.getStatus(flightId, passengerId);
        return ResponseEntity.ok(WaitlistDto.from(entry));
    }
}
