package com.nhnacademy.processing.service.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThresholdCheckerTest {

    @Mock private AnomalyCounter anomalyCounter;

    @InjectMocks private ThresholdChecker thresholdChecker;

    private final String devEui = "devEui1234";
    private final String measurement = "temperature";
    private final String expectedKey = devEui+":"+measurement;
    private final int threshold = 5;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(thresholdChecker, "threshold", threshold);
    }

    @Test
    @DisplayName("임계치 미만이면 알림 대상 아님")
    void shouldAlert_belowThreshold() {
        when(anomalyCounter.incrementAndGet("oor", expectedKey)).thenReturn((long)threshold-1);

        boolean result = thresholdChecker.shouldAlert(devEui, measurement);

        assertFalse(result);
        verify(anomalyCounter, never()).tryMarkAlerted(anyString(), anyString());
    }

    @Test
    @DisplayName("임계치 도달 및 쿨다운 통과 시 알림 대상")
    void shouldAlert_success() {
        when(anomalyCounter.incrementAndGet("oor", expectedKey)).thenReturn((long)threshold);
        when(anomalyCounter.tryMarkAlerted("oor", expectedKey)).thenReturn(true);

        boolean result= thresholdChecker.shouldAlert(devEui, measurement);

        assertTrue(result);
    }

    @Test
    @DisplayName("임계치에 도달했어도 이미 쿨다운 상태면 알림 대상 아님")
    void shouldAlert_InCooldown() {
        when(anomalyCounter.incrementAndGet("oor", expectedKey)).thenReturn((long) threshold);
        when(anomalyCounter.tryMarkAlerted("oor", expectedKey)).thenReturn(false);

        boolean result = thresholdChecker.shouldAlert(devEui, measurement);

        assertFalse(result);
    }

    @Test
    @DisplayName("Redis 예외 발생 시 애플리케이션이 죽지 않고 false 반환")
    void shouldAlert_ThrowsException_ReturnsFalse() {
        when(anomalyCounter.incrementAndGet("oor", expectedKey)).thenThrow(new RuntimeException("Redis down"));

        boolean result = thresholdChecker.shouldAlert(devEui, measurement);

        assertFalse(result);
    }
}
