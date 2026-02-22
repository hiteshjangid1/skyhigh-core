package com.skyhigh.application.waitlist;

import com.skyhigh.application.seat.SeatService;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.domain.waitlist.entity.WaitlistEntry;
import com.skyhigh.domain.waitlist.entity.WaitlistStatus;
import com.skyhigh.infrastructure.persistence.flight.FlightRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import com.skyhigh.infrastructure.persistence.waitlist.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository waitlistRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SeatService seatService;
    @Mock
    private SeatReservationRepository reservationRepository;
    @Mock
    private FlightRepository flightRepository;

    private WaitlistService waitlistService;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(
                waitlistRepository, seatRepository, seatService,
                reservationRepository, flightRepository);
    }

    @Test
    void joinWaitlist_succeeds() {
        when(flightRepository.existsById(100L)).thenReturn(true);
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.PENDING))
                .thenReturn(Optional.empty());
        when(waitlistRepository.save(any())).thenAnswer(i -> {
            WaitlistEntry e = i.getArgument(0);
            return WaitlistEntry.builder()
                    .id(1L)
                    .flightId(e.getFlightId())
                    .passengerId(e.getPassengerId())
                    .status(e.getStatus())
                    .build();
        });

        WaitlistEntry result = waitlistService.joinWaitlist(100L, "p1");

        assertEquals(WaitlistStatus.PENDING, result.getStatus());
        assertEquals("p1", result.getPassengerId());
    }

    @Test
    void joinWaitlist_whenFlightNotFound_throws() {
        when(flightRepository.existsById(999L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                waitlistService.joinWaitlist(999L, "p1"));
    }

    @Test
    void joinWaitlist_whenAlreadyOnWaitlist_throws() {
        when(flightRepository.existsById(100L)).thenReturn(true);
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.PENDING))
                .thenReturn(Optional.of(WaitlistEntry.builder().id(1L).build()));

        assertThrows(IllegalStateException.class, () ->
                waitlistService.joinWaitlist(100L, "p1"));
    }

    @Test
    void getStatus_returnsEntry() {
        WaitlistEntry entry = WaitlistEntry.builder()
                .id(1L)
                .flightId(100L)
                .passengerId("p1")
                .status(WaitlistStatus.PENDING)
                .build();
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.PENDING))
                .thenReturn(Optional.of(entry));

        WaitlistEntry result = waitlistService.getStatus(100L, "p1");

        assertEquals(WaitlistStatus.PENDING, result.getStatus());
    }

    @Test
    void getStatus_fallsBackToAssigned() {
        WaitlistEntry entry = WaitlistEntry.builder()
                .id(1L)
                .flightId(100L)
                .passengerId("p1")
                .status(WaitlistStatus.ASSIGNED)
                .build();
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.PENDING))
                .thenReturn(Optional.empty());
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.ASSIGNED))
                .thenReturn(Optional.of(entry));

        WaitlistEntry result = waitlistService.getStatus(100L, "p1");

        assertEquals(WaitlistStatus.ASSIGNED, result.getStatus());
    }

    @Test
    void getStatus_whenNotFound_throws() {
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.PENDING))
                .thenReturn(Optional.empty());
        when(waitlistRepository.findByFlightIdAndPassengerIdAndStatus(100L, "p1", WaitlistStatus.ASSIGNED))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                waitlistService.getStatus(100L, "p1"));
    }

    @Test
    void getWaitlistForFlight_returnsList() {
        List<WaitlistEntry> entries = List.of(
                WaitlistEntry.builder().id(1L).flightId(100L).passengerId("p1").status(WaitlistStatus.PENDING).build());
        when(waitlistRepository.findByFlightIdAndStatusOrderByCreatedAtAsc(100L, WaitlistStatus.PENDING))
                .thenReturn(entries);

        List<WaitlistEntry> result = waitlistService.getWaitlistForFlight(100L);

        assertEquals(1, result.size());
        assertEquals("p1", result.get(0).getPassengerId());
    }

    @Test
    void onSeatReleased_assignsToWaitlist() {
        SeatService.SeatReleasedEvent event = new SeatService.SeatReleasedEvent(100L, 5L);
        WaitlistEntry next = WaitlistEntry.builder()
                .id(1L)
                .flightId(100L)
                .passengerId("p1")
                .status(WaitlistStatus.PENDING)
                .build();
        SeatReservation reservation = SeatReservation.builder().id(10L).build();
        when(waitlistRepository.findFirstByFlightIdAndStatusOrderByCreatedAtAsc(100L, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(next));
        when(seatService.holdSeat(100L, 5L, "p1")).thenReturn(reservation);
        when(waitlistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        waitlistService.onSeatReleased(event);

        verify(seatService).holdSeat(100L, 5L, "p1");
        verify(waitlistRepository).save(argThat(e -> e.getStatus() == WaitlistStatus.ASSIGNED));
    }

    @Test
    void onSeatReleased_whenNoWaitlist_doesNothing() {
        SeatService.SeatReleasedEvent event = new SeatService.SeatReleasedEvent(100L, 5L);
        when(waitlistRepository.findFirstByFlightIdAndStatusOrderByCreatedAtAsc(100L, WaitlistStatus.PENDING))
                .thenReturn(Optional.empty());

        waitlistService.onSeatReleased(event);

        verify(seatService, never()).holdSeat(any(), any(), any());
    }

    @Test
    void onSeatReleased_whenHoldFails_continuesWithoutThrowing() {
        SeatService.SeatReleasedEvent event = new SeatService.SeatReleasedEvent(100L, 5L);
        WaitlistEntry next = WaitlistEntry.builder()
                .id(1L)
                .flightId(100L)
                .passengerId("p1")
                .status(WaitlistStatus.PENDING)
                .build();
        when(waitlistRepository.findFirstByFlightIdAndStatusOrderByCreatedAtAsc(100L, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(next));
        when(seatService.holdSeat(100L, 5L, "p1")).thenThrow(new RuntimeException("Seat taken"));

        assertDoesNotThrow(() -> waitlistService.onSeatReleased(event));
    }
}
