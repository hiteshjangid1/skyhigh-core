package com.skyhigh.application.checkin;

import com.skyhigh.application.seat.SeatService;
import com.skyhigh.domain.baggage.entity.BaggageRecord;
import com.skyhigh.domain.checkin.entity.CheckIn;
import com.skyhigh.domain.checkin.entity.CheckInStatus;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.infrastructure.external.PaymentServiceClient;
import com.skyhigh.infrastructure.external.WeightServiceClient;
import com.skyhigh.infrastructure.persistence.baggage.BaggageRecordRepository;
import com.skyhigh.infrastructure.persistence.checkin.CheckInRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private SeatService seatService;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private BaggageRecordRepository baggageRecordRepository;
    @Mock
    private SeatReservationRepository reservationRepository;
    @Mock
    private WeightServiceClient weightServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    private CheckInService checkInService;

    @BeforeEach
    void setUp() {
        checkInService = new CheckInService(
                seatService, checkInRepository, baggageRecordRepository,
                reservationRepository, weightServiceClient, paymentServiceClient);
        ReflectionTestUtils.setField(checkInService, "maxWeightKg", new BigDecimal("25"));
    }

    @Test
    void startCheckIn_succeeds() {
        SeatReservation reservation = SeatReservation.builder().id(10L).build();
        when(seatService.holdSeat(100L, 5L, "p1")).thenReturn(reservation);
        when(checkInRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckIn result = checkInService.startCheckIn(100L, "p1", 5L);

        assertEquals(CheckInStatus.IN_PROGRESS, result.getStatus());
        assertEquals(10L, result.getReservationId());
        assertEquals("p1", result.getPassengerId());
    }

    @Test
    void addBaggage_whenUnderweight_continues() {
        CheckIn checkIn = CheckIn.builder()
                .id(1L)
                .passengerId("p1")
                .status(CheckInStatus.IN_PROGRESS)
                .build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));
        when(weightServiceClient.getWeight("b1")).thenReturn(
                WeightServiceClient.WeightResult.builder()
                        .weightKg(new BigDecimal("20"))
                        .valid(true)
                        .build());
        when(baggageRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckIn result = checkInService.addBaggage(1L, "b1", "p1");

        assertEquals(CheckInStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void addBaggage_whenOverweight_setsAwaitingPayment() {
        CheckIn checkIn = CheckIn.builder()
                .id(1L)
                .passengerId("p1")
                .status(CheckInStatus.IN_PROGRESS)
                .build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));
        when(weightServiceClient.getWeight("overweight-b1")).thenReturn(
                WeightServiceClient.WeightResult.builder()
                        .weightKg(new BigDecimal("30"))
                        .valid(true)
                        .build());
        when(baggageRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(checkInRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckIn result = checkInService.addBaggage(1L, "overweight-b1", "p1");

        assertEquals(CheckInStatus.AWAITING_PAYMENT, result.getStatus());
    }

    @Test
    void addBaggage_whenCheckInNotFound_throws() {
        when(checkInRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                checkInService.addBaggage(999L, "b1", "p1"));
    }

    @Test
    void addBaggage_whenWrongPassenger_throws() {
        CheckIn checkIn = CheckIn.builder().id(1L).passengerId("p1").status(CheckInStatus.IN_PROGRESS).build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));

        assertThrows(IllegalArgumentException.class, () ->
                checkInService.addBaggage(1L, "b1", "p2"));
    }

    @Test
    void addBaggage_whenCompleted_throws() {
        CheckIn checkIn = CheckIn.builder().id(1L).passengerId("p1").status(CheckInStatus.COMPLETED).build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));

        assertThrows(IllegalStateException.class, () ->
                checkInService.addBaggage(1L, "b1", "p1"));
    }

    @Test
    void completePayment_succeeds() {
        CheckIn checkIn = CheckIn.builder()
                .id(1L)
                .passengerId("p1")
                .reservationId(10L)
                .status(CheckInStatus.AWAITING_PAYMENT)
                .build();
        BaggageRecord unpaid = BaggageRecord.builder()
                .id(1L)
                .checkInId(1L)
                .feeAmount(new BigDecimal("25"))
                .paid(false)
                .build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));
        when(baggageRecordRepository.findByCheckInId(1L)).thenReturn(List.of(unpaid));
        when(paymentServiceClient.processPayment(any(), any())).thenReturn(
                PaymentServiceClient.PaymentResult.builder().success(true).build());
        when(baggageRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(checkInRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckIn result = checkInService.completePayment(1L, "pay-ref", "p1");

        assertEquals(CheckInStatus.COMPLETED, result.getStatus());
        verify(seatService).confirmSeat(10L, "p1");
    }

    @Test
    void completePayment_whenPaymentFails_throws() {
        CheckIn checkIn = CheckIn.builder()
                .id(1L)
                .passengerId("p1")
                .status(CheckInStatus.AWAITING_PAYMENT)
                .build();
        BaggageRecord unpaid = BaggageRecord.builder().feeAmount(new BigDecimal("25")).paid(false).build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));
        when(baggageRecordRepository.findByCheckInId(1L)).thenReturn(List.of(unpaid));
        when(paymentServiceClient.processPayment(any(), any())).thenReturn(
                PaymentServiceClient.PaymentResult.builder().success(false).message("Declined").build());

        assertThrows(IllegalStateException.class, () ->
                checkInService.completePayment(1L, "pay-ref", "p1"));
    }

    @Test
    void completePayment_whenNoUnpaidBaggage_completesDirectly() {
        CheckIn checkIn = CheckIn.builder()
                .id(1L)
                .passengerId("p1")
                .reservationId(10L)
                .status(CheckInStatus.AWAITING_PAYMENT)
                .build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));
        when(baggageRecordRepository.findByCheckInId(1L)).thenReturn(List.of());
        when(checkInRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckIn result = checkInService.completePayment(1L, "pay-ref", "p1");

        assertEquals(CheckInStatus.COMPLETED, result.getStatus());
    }

    @Test
    void getCheckIn_succeeds() {
        CheckIn checkIn = CheckIn.builder().id(1L).passengerId("p1").build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));

        CheckIn result = checkInService.getCheckIn(1L, "p1");

        assertEquals(1L, result.getId());
    }

    @Test
    void getCheckIn_whenWrongPassenger_throws() {
        CheckIn checkIn = CheckIn.builder().id(1L).passengerId("p1").build();
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));

        assertThrows(IllegalArgumentException.class, () ->
                checkInService.getCheckIn(1L, "p2"));
    }
}
