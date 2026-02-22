package com.skyhigh.infrastructure.persistence.abuse;

import com.skyhigh.domain.abuse.entity.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    @Query("SELECT COUNT(DISTINCT a.flightId) FROM AccessLog a WHERE a.sourceId = :sourceId AND a.accessedAt >= :since")
    long countDistinctFlightsAccessedBySourceSince(@Param("sourceId") String sourceId, @Param("since") Instant since);
}
