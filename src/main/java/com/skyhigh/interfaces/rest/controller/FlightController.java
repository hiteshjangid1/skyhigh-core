package com.skyhigh.interfaces.rest.controller;

import com.skyhigh.domain.flight.entity.Flight;
import com.skyhigh.infrastructure.persistence.flight.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightRepository flightRepository;

    @GetMapping
    public ResponseEntity<List<Flight>> listFlights() {
        return ResponseEntity.ok(flightRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Flight> getFlight(@PathVariable Long id) {
        return flightRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
