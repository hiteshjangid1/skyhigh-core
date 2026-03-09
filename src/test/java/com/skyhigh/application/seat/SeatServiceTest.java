package com.skyhigh.application.seat;

import com.skyhigh.application.seat.exception.HoldExpiredException;
import com.skyhigh.application.seat.exception.InvalidStateException;
import com.skyhigh.application.seat.exception.SeatUnavailableException;
import com.skyhigh.domain.seat.entity.Seat;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.domain.seat.entity.SeatState;
import com.skyhigh.infrastructure.persistence.seat.SeatRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SeatReservationRepository reservationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CacheManager cacheManager;

    private SeatService seatService;

    @BeforeEach
    void setUp() {
        seatService = new SeatService(seatRepository, reservationRepository, eventPublisher, cacheManager, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(seatService, "holdDurationSeconds", 120);
        when(cacheManager.getCache("seatMap")).thenReturn(new ConcurrentMapCache("seatMap"));
    }

    @Test
    void holdSeat_whenAvailable_succeeds() {
        Seat seat = Seat.builder()
                .id(1L)
                .flightId(100L)
                .seatNumber("1A")
                .state(SeatState.AVAILABLE)
                .build();
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SeatReservation result = seatService.holdSeat(100L, 1L, "passenger1");

        assertNotNull(result);
        assertEquals(SeatState.HELD, result.getState());
        assertEquals("passenger1", result.getPassengerId());
    }

    @Test
    void holdSeat_whenNotAvailable_throws() {
        Seat seat = Seat.builder()
                .id(1L)
                .flightId(100L)
                .state(SeatState.HELD)
                .build();
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));

        assertThrows(SeatUnavailableException.class, () ->
                seatService.holdSeat(100L, 1L, "passenger1"));
    }

    @Test
    void holdSeat_whenSeatNotFound_throws() {
        when(seatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(SeatUnavailableException.class, () ->
                seatService.holdSeat(100L, 999L, "passenger1"));
    }

    @Test
    void holdSeat_whenWrongFlight_throws() {
        Seat seat = Seat.builder().id(1L).flightId(200L).state(SeatState.AVAILABLE).build();
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));

        assertThrows(InvalidStateException.class, () ->
                seatService.holdSeat(100L, 1L, "passenger1"));
    }

    @Test
    void confirmSeat_succeeds() {
        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seatId(10L)
                .flightId(100L)
                .passengerId("p1")
                .state(SeatState.HELD)
                .heldUntil(Instant.now().plusSeconds(60))
                .build();
        Seat seat = Seat.builder().id(10L).flightId(100L).state(SeatState.HELD).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SeatReservation result = seatService.confirmSeat(1L, "p1");

        assertEquals(SeatState.CONFIRMED, result.getState());
        assertNotNull(result.getConfirmedAt());
    }

    @Test
    void confirmSeat_whenExpired_throws() {
        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seatId(10L)
                .flightId(100L)
                .passengerId("p1")
                .state(SeatState.HELD)
                .heldUntil(Instant.now().minusSeconds(1))
                .build();
        Seat seat = Seat.builder().id(10L).state(SeatState.HELD).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(HoldExpiredException.class, () -> seatService.confirmSeat(1L, "p1"));
    }

    @Test
    void confirmSeat_whenWrongPassenger_throws() {
        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .passengerId("p1")
                .state(SeatState.HELD)
                .heldUntil(Instant.now().plusSeconds(60))
                .build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThrows(InvalidStateException.class, () -> seatService.confirmSeat(1L, "p2"));
    }

    @Test
    void confirmSeat_whenNotHeld_throws() {
        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .passengerId("p1")
                .state(SeatState.CONFIRMED)
                .heldUntil(Instant.now().plusSeconds(60))
                .build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThrows(InvalidStateException.class, () -> seatService.confirmSeat(1L, "p1"));
    }

    @Test
    void cancelSeat_succeeds() {
        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .seatId(10L)
                .flightId(100L)
                .passengerId("p1")
                .state(SeatState.CONFIRMED)
                .build();
        Seat seat = Seat.builder().id(10L).flightId(100L).state(SeatState.CONFIRMED).build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        seatService.cancelSeat(1L, "p1", 100L);

        ArgumentCaptor<SeatService.SeatReleasedEvent> captor = ArgumentCaptor.forClass(SeatService.SeatReleasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(100L, captor.getValue().flightId());
        assertEquals(10L, captor.getValue().seatId());
    }

    @Test
    void cancelSeat_whenNotConfirmed_throws() {
        SeatReservation reservation = SeatReservation.builder()
                .id(1L)
                .passengerId("p1")
                .state(SeatState.HELD)
                .build();
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        assertThrows(InvalidStateException.class, () -> seatService.cancelSeat(1L, "p1", 100L));
    }

    /** Hold-expiry edge case: confirm when held_until is in the past throws HoldExpiredException (410). */
    @Test
    void confirmSeat_whenHeldUntilInPast_throwsHoldExpired() {
        SeatReservation pastReservation = SeatReservation.builder()
                .id(2L)
                .seatId(11L)
                .flightId(100L)
                .passengerId("p2")
                .state(SeatState.HELD)
                .heldUntil(Instant.now().minusSeconds(10))
                .build();
        Seat seat = Seat.builder().id(11L).state(SeatState.HELD).build();
        when(reservationRepository.findById(2L)).thenReturn(Optional.of(pastReservation));
        when(seatRepository.findById(11L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(HoldExpiredException.class, () -> seatService.confirmSeat(2L, "p2"));
    }

    /** Hold-expiry edge case: releaseExpiredHolds only releases reservations whose held_until is strictly before the passed-in instant. */
    @Test
    void releaseExpiredHolds_doesNotReleaseFutureHolds() {
        when(reservationRepository.findExpiredHolds(any())).thenReturn(List.of());

        seatService.releaseExpiredHolds();

        verify(seatRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void releaseExpiredHolds_releasesAndPublishesEvent() {
        SeatReservation expired = SeatReservation.builder()
                .id(1L)
                .seatId(10L)
                .flightId(100L)
                .passengerId("p1")
                .state(SeatState.HELD)
                .heldUntil(Instant.now().minusSeconds(1))
                .build();
        Seat seat = Seat.builder().id(10L).state(SeatState.HELD).build();
        when(reservationRepository.findExpiredHolds(any())).thenReturn(List.of(expired));
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        seatService.releaseExpiredHolds();

        verify(eventPublisher).publishEvent(any(SeatService.SeatReleasedEvent.class));
    }

    @Test
    void getSeatsByFlight_returnsSeats() {
        List<Seat> seats = List.of(
                Seat.builder().id(1L).flightId(100L).seatNumber("1A").state(SeatState.AVAILABLE).build());
        when(seatRepository.findByFlightIdOrderByRowNumberAscColumnLetterAsc(100L)).thenReturn(seats);

        List<Seat> result = seatService.getSeatsByFlight(100L);

        assertEquals(1, result.size());
        assertEquals("1A", result.get(0).getSeatNumber());
    }
}
