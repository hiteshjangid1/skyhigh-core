package com.skyhigh.application.checkin;

import com.skyhigh.application.seat.SeatService;
import com.skyhigh.domain.baggage.entity.BaggageRecord;
import com.skyhigh.domain.checkin.entity.CheckIn;
import com.skyhigh.domain.checkin.entity.CheckInStatus;
import com.skyhigh.domain.seat.entity.SeatReservation;
import com.skyhigh.domain.seat.entity.SeatState;
import com.skyhigh.infrastructure.external.PaymentServiceClient;
import com.skyhigh.infrastructure.external.WeightServiceClient;
import com.skyhigh.infrastructure.persistence.baggage.BaggageRecordRepository;
import com.skyhigh.infrastructure.persistence.checkin.CheckInRepository;
import com.skyhigh.infrastructure.persistence.seat.SeatReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckInService {

    private final SeatService seatService;
    private final CheckInRepository checkInRepository;
    private final BaggageRecordRepository baggageRecordRepository;
    private final SeatReservationRepository reservationRepository;
    private final WeightServiceClient weightServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Value("${skyhigh.baggage.max-weight-kg:25}")
    private BigDecimal maxWeightKg;

    private static final BigDecimal FEE_PER_KG_OVER = new BigDecimal("5.00");

    @Transactional
    public CheckIn startCheckIn(Long flightId, String passengerId, Long seatId) {
        SeatReservation reservation = seatService.holdSeat(flightId, seatId, passengerId);
        CheckIn checkIn = CheckIn.builder()
                .flightId(flightId)
                .passengerId(passengerId)
                .reservationId(reservation.getId())
                .status(CheckInStatus.IN_PROGRESS)
                .build();
        return checkInRepository.save(checkIn);
    }

    @Transactional
    public CheckIn addBaggage(Long checkInId, String baggageId, String passengerId) {
        CheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new IllegalArgumentException("Check-in not found"));

        if (!checkIn.getPassengerId().equals(passengerId)) {
            throw new IllegalArgumentException("Check-in does not belong to passenger");
        }

        if (checkIn.getStatus() == CheckInStatus.COMPLETED) {
            throw new IllegalStateException("Check-in already completed");
        }

        WeightServiceClient.WeightResult weightResult = weightServiceClient.getWeight(baggageId);
        BigDecimal weightKg = weightResult.getWeightKg();

        BaggageRecord record = BaggageRecord.builder()
                .checkInId(checkInId)
                .baggageId(baggageId)
                .weightKg(weightKg)
                .paid(false)
                .build();

        if (weightKg.compareTo(maxWeightKg) > 0) {
            BigDecimal excessKg = weightKg.subtract(maxWeightKg);
            BigDecimal fee = excessKg.multiply(FEE_PER_KG_OVER);
            record.setFeeAmount(fee);
            record.setPaid(false);
            baggageRecordRepository.save(record);
            checkIn.setStatus(CheckInStatus.AWAITING_PAYMENT);
            return checkInRepository.save(checkIn);
        }

        record.setFeeAmount(BigDecimal.ZERO);
        record.setPaid(true);
        baggageRecordRepository.save(record);
        return checkIn;
    }

    @Transactional
    public CheckIn completePayment(Long checkInId, String paymentRef, String passengerId) {
        CheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new IllegalArgumentException("Check-in not found"));

        if (!checkIn.getPassengerId().equals(passengerId)) {
            throw new IllegalArgumentException("Check-in does not belong to passenger");
        }

        if (checkIn.getStatus() != CheckInStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException("Check-in is not awaiting payment");
        }

        List<BaggageRecord> unpaidBaggage = baggageRecordRepository.findByCheckInId(checkInId).stream()
                .filter(b -> !b.getPaid() && b.getFeeAmount() != null && b.getFeeAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal totalFee = unpaidBaggage.stream()
                .map(BaggageRecord::getFeeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalFee.compareTo(BigDecimal.ZERO) <= 0) {
            return completeCheckIn(checkIn);
        }

        var paymentResult = paymentServiceClient.processPayment(paymentRef, totalFee);
        if (!paymentResult.isSuccess()) {
            throw new IllegalStateException("Payment failed: " + paymentResult.getMessage());
        }

        unpaidBaggage.forEach(b -> {
            b.setPaid(true);
            baggageRecordRepository.save(b);
        });

        return completeCheckIn(checkIn);
    }

    private CheckIn completeCheckIn(CheckIn checkIn) {
        checkIn.setStatus(CheckInStatus.COMPLETED);
        checkIn.setCompletedAt(Instant.now());
        checkIn = checkInRepository.save(checkIn);

        if (checkIn.getReservationId() != null) {
            seatService.confirmSeat(checkIn.getReservationId(), checkIn.getPassengerId());
        }
        return checkIn;
    }

    public CheckIn getCheckIn(Long id, String passengerId) {
        CheckIn checkIn = checkInRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Check-in not found"));
        if (!checkIn.getPassengerId().equals(passengerId)) {
            throw new IllegalArgumentException("Check-in does not belong to passenger");
        }
        return checkIn;
    }
}
