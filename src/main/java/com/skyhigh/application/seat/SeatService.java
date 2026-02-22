package com.skyhigh.application.seat;

import com.skyhigh.application.seat.exception.HoldExpiredException;
import com.skyhigh.application.seat.exception.InvalidStateException;
import com.skyhigh.application.seat.exception.SeatUnavailableException;
import com.skyhigh.domain.seat.entity.Seat;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.domain.seat.entity.SeatState;
import com.skyhigh.infrastructure.persistence.seat.SeatRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    @Value("${skyhigh.seat.hold-duration-seconds:120}")
    private int holdDurationSeconds;

    private static final String SEAT_MAP_CACHE = "seatMap";

    @Transactional
    @CacheEvict(value = SEAT_MAP_CACHE, key = "#flightId")
    public SeatReservation holdSeat(Long flightId, Long seatId, String passengerId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new SeatUnavailableException("Seat not found: " + seatId));

        if (!seat.getFlightId().equals(flightId)) {
            throw new InvalidStateException("Seat does not belong to flight: " + flightId);
        }

        if (seat.getState() != SeatState.AVAILABLE) {
            throw new SeatUnavailableException("Seat " + seat.getSeatNumber() + " is not available");
        }

        Instant now = Instant.now();
        Instant heldUntil = now.plusSeconds(holdDurationSeconds);

        seat.setState(SeatState.HELD);
        seatRepository.save(seat);

        SeatReservation reservation = SeatReservation.builder()
                .seatId(seatId)
                .flightId(flightId)
                .passengerId(passengerId)
                .state(SeatState.HELD)
                .heldAt(now)
                .heldUntil(heldUntil)
                .build();
        return reservationRepository.save(reservation);
    }

    @Transactional
    public SeatReservation confirmSeat(Long reservationId, String passengerId) {
        SeatReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InvalidStateException("Reservation not found: " + reservationId));

        if (!reservation.getPassengerId().equals(passengerId)) {
            throw new InvalidStateException("Reservation does not belong to passenger");
        }

        if (reservation.getState() != SeatState.HELD) {
            throw new InvalidStateException("Reservation is not in HELD state");
        }

        if (reservation.getHeldUntil().isBefore(Instant.now())) {
            releaseHold(reservation);
            throw new HoldExpiredException("Seat hold has expired");
        }

        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new InvalidStateException("Seat not found"));

        seat.setState(SeatState.CONFIRMED);
        seatRepository.save(seat);

        Instant now = Instant.now();
        reservation.setState(SeatState.CONFIRMED);
        reservation.setConfirmedAt(now);
        reservation.setHeldUntil(null);
        SeatReservation saved = reservationRepository.save(reservation);
        evictSeatMapCache(reservation.getFlightId());
        return saved;
    }

    @Transactional
    @CacheEvict(value = SEAT_MAP_CACHE, key = "#flightId")
    public void cancelSeat(Long reservationId, String passengerId, Long flightId) {
        SeatReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new InvalidStateException("Reservation not found: " + reservationId));

        if (!reservation.getPassengerId().equals(passengerId)) {
            throw new InvalidStateException("Reservation does not belong to passenger");
        }

        if (reservation.getState() != SeatState.CONFIRMED) {
            throw new InvalidStateException("Only confirmed reservations can be cancelled");
        }

        Seat seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new InvalidStateException("Seat not found"));

        seat.setState(SeatState.AVAILABLE);
        seatRepository.save(seat);

        Instant now = Instant.now();
        reservation.setState(SeatState.CANCELLED);
        reservation.setCancelledAt(now);
        reservationRepository.save(reservation);

        eventPublisher.publishEvent(new SeatReleasedEvent(reservation.getFlightId(), reservation.getSeatId()));
    }

    @Scheduled(fixedRate = 15000)
    @Transactional
    public void releaseExpiredHolds() {
        List<SeatReservation> expired = reservationRepository.findExpiredHolds(Instant.now());
        for (SeatReservation reservation : expired) {
            try {
                releaseHold(reservation);
                eventPublisher.publishEvent(new SeatReleasedEvent(reservation.getFlightId(), reservation.getSeatId()));
            } catch (Exception e) {
                log.warn("Failed to release expired hold {}: {}", reservation.getId(), e.getMessage());
            }
        }
    }

    private void releaseHold(SeatReservation reservation) {
        Seat seat = seatRepository.findById(reservation.getSeatId()).orElse(null);
        if (seat != null && seat.getState() == SeatState.HELD) {
            seat.setState(SeatState.AVAILABLE);
            seatRepository.save(seat);
        }
        reservation.setState(SeatState.CANCELLED);
        reservation.setCancelledAt(Instant.now());
        reservationRepository.save(reservation);
    }

    @Cacheable(value = SEAT_MAP_CACHE, key = "#flightId")
    public List<Seat> getSeatsByFlight(Long flightId) {
        return seatRepository.findByFlightIdOrderByRowNumberAscColumnLetterAsc(flightId);
    }

    private void evictSeatMapCache(Long flightId) {
        var cache = cacheManager.getCache(SEAT_MAP_CACHE);
        if (cache != null) {
            cache.evict(flightId);
        }
    }

    public record SeatReleasedEvent(Long flightId, Long seatId) {}
}
