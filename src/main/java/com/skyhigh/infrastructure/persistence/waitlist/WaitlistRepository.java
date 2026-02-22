package com.skyhigh.infrastructure.persistence.waitlist;

import com.skyhigh.domain.waitlist.entity.WaitlistEntry;
import com.skyhigh.domain.waitlist.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByFlightIdAndStatusOrderByCreatedAtAsc(Long flightId, WaitlistStatus status);

    Optional<WaitlistEntry> findFirstByFlightIdAndStatusOrderByCreatedAtAsc(Long flightId, WaitlistStatus status);

    Optional<WaitlistEntry> findByFlightIdAndPassengerIdAndStatus(Long flightId, String passengerId, WaitlistStatus status);
}
