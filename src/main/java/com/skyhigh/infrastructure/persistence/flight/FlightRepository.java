package com.skyhigh.infrastructure.persistence.flight;

import com.skyhigh.domain.flight.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);
}
