package com.skyhigh.application.abuse;

import com.skyhigh.domain.abuse.entity.AccessLog;
import com.skyhigh.domain.abuse.entity.AbuseEvent;
import com.skyhigh.infrastructure.persistence.abuse.AccessLogRepository;
import com.skyhigh.infrastructure.persistence.abuse.AbuseEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AbuseDetectionService {

    private final AccessLogRepository accessLogRepository;
    private final AbuseEventRepository abuseEventRepository;

    @Value("${skyhigh.abuse.threshold-count:50}")
    private int thresholdCount;

    @Value("${skyhigh.abuse.threshold-window-seconds:2}")
    private int thresholdWindowSeconds;

    @Transactional
    public void recordAccess(String sourceId, Long flightId, String endpoint) {
        AccessLog log = AccessLog.builder()
                .sourceId(sourceId)
                .flightId(flightId)
                .accessedAt(Instant.now())
                .endpoint(endpoint)
                .build();
        accessLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public void checkAbuse(String sourceId) {
        Instant since = Instant.now().minusSeconds(thresholdWindowSeconds);
        long distinctFlights = accessLogRepository.countDistinctFlightsAccessedBySourceSince(sourceId, since);

        if (distinctFlights >= thresholdCount) {
            AbuseEvent event = AbuseEvent.builder()
                    .sourceId(sourceId)
                    .accessCount((int) distinctFlights)
                    .windowSeconds(thresholdWindowSeconds)
                    .detectedAt(Instant.now())
                    .build();
            abuseEventRepository.save(event);
            throw new AbuseDetectedException("Abusive access pattern detected. Access temporarily restricted.");
        }
    }
}
