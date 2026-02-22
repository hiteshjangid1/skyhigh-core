package com.skyhigh.application.abuse;

import com.skyhigh.domain.abuse.entity.AccessLog;
import com.skyhigh.infrastructure.persistence.abuse.AccessLogRepository;
import com.skyhigh.infrastructure.persistence.abuse.AbuseEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbuseDetectionServiceTest {

    @Mock
    private AccessLogRepository accessLogRepository;
    @Mock
    private AbuseEventRepository abuseEventRepository;

    private AbuseDetectionService abuseDetectionService;

    @BeforeEach
    void setUp() {
        abuseDetectionService = new AbuseDetectionService(accessLogRepository, abuseEventRepository);
        ReflectionTestUtils.setField(abuseDetectionService, "thresholdCount", 50);
        ReflectionTestUtils.setField(abuseDetectionService, "thresholdWindowSeconds", 2);
    }

    @Test
    void recordAccess_savesLog() {
        when(accessLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        abuseDetectionService.recordAccess("192.168.1.1", 100L, "/flights/100/seats");

        ArgumentCaptor<AccessLog> captor = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogRepository).save(captor.capture());
        assertEquals("192.168.1.1", captor.getValue().getSourceId());
        assertEquals(100L, captor.getValue().getFlightId());
    }

    @Test
    void checkAbuse_whenBelowThreshold_doesNotThrow() {
        when(accessLogRepository.countDistinctFlightsAccessedBySourceSince(any(), any())).thenReturn(10L);

        assertDoesNotThrow(() -> abuseDetectionService.checkAbuse("192.168.1.1"));
        verify(abuseEventRepository, never()).save(any());
    }

    @Test
    void checkAbuse_whenAtThreshold_throwsAndSavesEvent() {
        when(accessLogRepository.countDistinctFlightsAccessedBySourceSince(any(), any())).thenReturn(50L);
        when(abuseEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(AbuseDetectedException.class, () ->
                abuseDetectionService.checkAbuse("192.168.1.1"));

        ArgumentCaptor<com.skyhigh.domain.abuse.entity.AbuseEvent> captor =
                ArgumentCaptor.forClass(com.skyhigh.domain.abuse.entity.AbuseEvent.class);
        verify(abuseEventRepository).save(captor.capture());
        assertEquals("192.168.1.1", captor.getValue().getSourceId());
        assertEquals(50, captor.getValue().getAccessCount());
    }

    @Test
    void checkAbuse_whenAboveThreshold_throws() {
        when(accessLogRepository.countDistinctFlightsAccessedBySourceSince(any(), any())).thenReturn(100L);
        when(abuseEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AbuseDetectedException ex = assertThrows(AbuseDetectedException.class, () ->
                abuseDetectionService.checkAbuse("192.168.1.1"));

        assertTrue(ex.getMessage().contains("Abusive access pattern"));
    }
}
