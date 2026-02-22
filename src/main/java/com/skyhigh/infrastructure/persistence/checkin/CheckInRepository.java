package com.skyhigh.infrastructure.persistence.checkin;

import com.skyhigh.domain.checkin.entity.CheckIn;
import com.skyhigh.domain.checkin.entity.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findByPassengerIdAndFlightIdAndStatus(String passengerId, Long flightId, CheckInStatus status);

    List<CheckIn> findByPassengerId(String passengerId);
}
