package com.skyhigh.infrastructure.persistence.abuse;

import com.skyhigh.domain.abuse.entity.AbuseEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AbuseEventRepository extends JpaRepository<AbuseEvent, Long> {

    List<AbuseEvent> findAllByOrderByDetectedAtDesc(Pageable pageable);

    List<AbuseEvent> findBySourceIdOrderByDetectedAtDesc(String sourceId, Pageable pageable);

    List<AbuseEvent> findByDetectedAtAfterOrderByDetectedAtDesc(Instant since, Pageable pageable);

    List<AbuseEvent> findBySourceIdAndDetectedAtAfterOrderByDetectedAtDesc(String sourceId, Instant since, Pageable pageable);
}
