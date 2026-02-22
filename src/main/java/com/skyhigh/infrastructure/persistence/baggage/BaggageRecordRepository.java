package com.skyhigh.infrastructure.persistence.baggage;

import com.skyhigh.domain.baggage.entity.BaggageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaggageRecordRepository extends JpaRepository<BaggageRecord, Long> {

    List<BaggageRecord> findByCheckInId(Long checkInId);
}
