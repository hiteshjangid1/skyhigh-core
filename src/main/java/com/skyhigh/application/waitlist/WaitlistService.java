package com.skyhigh.application.waitlist;

import com.skyhigh.application.seat.SeatService;
import com.skyhigh.domain.seat.entity.Seat;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.domain.waitlist.entity.WaitlistEntry;
import com.skyhigh.domain.waitlist.entity.WaitlistStatus;
import com.skyhigh.infrastructure.persistence.flight.FlightRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import com.skyhigh.infrastructure.persistence.waitlist.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final SeatRepository seatRepository;
    private final SeatService seatService;
    private final SeatReservationRepository reservationRepository;
    private final FlightRepository flightRepository;

    @Transactional
    public WaitlistEntry joinWaitlist(Long flightId, String passengerId) {
        if (!flightRepository.existsById(flightId)) {
            throw new IllegalArgumentException("Flight not found");
        }

        waitlistRepository.findByFlightIdAndPassengerIdAndStatus(flightId, passengerId, WaitlistStatus.PENDING)
                .ifPresent(e -> {
                    throw new IllegalStateException("Passenger already on waitlist for this flight");
                });

        return waitlistRepository.save(WaitlistEntry.builder()
                .flightId(flightId)
                .passengerId(passengerId)
                .status(WaitlistStatus.PENDING)
                .build());
    }

    @EventListener
    @Async
    @Transactional
    public void onSeatReleased(SeatService.SeatReleasedEvent event) {
        Long flightId = event.flightId();
        Long seatId = event.seatId();

        WaitlistEntry next = waitlistRepository.findFirstByFlightIdAndStatusOrderByCreatedAtAsc(flightId, WaitlistStatus.PENDING)
                .orElse(null);

        if (next == null) {
            return;
        }

        try {
            SeatReservation reservation = seatService.holdSeat(flightId, seatId, next.getPassengerId());
            next.setStatus(WaitlistStatus.ASSIGNED);
            next.setReservationId(reservation.getId());
            next.setAssignedAt(Instant.now());
            waitlistRepository.save(next);
            log.info("Assigned seat {} to waitlisted passenger {} for flight {}", seatId, next.getPassengerId(), flightId);
        } catch (Exception e) {
            log.warn("Failed to assign seat {} to waitlist: {}", seatId, e.getMessage());
        }
    }

    public WaitlistEntry getStatus(Long flightId, String passengerId) {
        return waitlistRepository.findByFlightIdAndPassengerIdAndStatus(flightId, passengerId, WaitlistStatus.PENDING)
                .or(() -> waitlistRepository.findByFlightIdAndPassengerIdAndStatus(flightId, passengerId, WaitlistStatus.ASSIGNED))
                .orElseThrow(() -> new IllegalArgumentException("No waitlist entry found"));
    }

    public List<WaitlistEntry> getWaitlistForFlight(Long flightId) {
        return waitlistRepository.findByFlightIdAndStatusOrderByCreatedAtAsc(flightId, WaitlistStatus.PENDING);
    }
}
