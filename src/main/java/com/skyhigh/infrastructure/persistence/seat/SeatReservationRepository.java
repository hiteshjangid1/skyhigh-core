package com.skyhigh.infrastructure.persistence.seat;

import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.domain.seat.entity.SeatState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    List<SeatReservation> findBySeatIdAndState(Long seatId, SeatState state);

    Optional<SeatReservation> findBySeatIdAndStateAndPassengerId(Long seatId, SeatState state, String passengerId);

    @Query("SELECT sr FROM SeatReservation sr WHERE sr.state = 'HELD' AND sr.heldUntil < :now")
    List<SeatReservation> findExpiredHolds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sr FROM SeatReservation sr WHERE sr.id = :id")
    Optional<SeatReservation> findByIdForUpdate(@Param("id") Long id);

    Optional<SeatReservation> findByIdAndPassengerId(Long id, String passengerId);
}
