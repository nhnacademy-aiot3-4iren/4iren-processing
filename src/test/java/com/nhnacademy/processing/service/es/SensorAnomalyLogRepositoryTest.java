package com.nhnacademy.processing.service.es;

import com.nhnacademy.processing.domain.SensorAnomalyLogDocument;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.repository.SensorAnomalyLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorAnomalyLogRepositoryTest {

    @Mock
    private SensorAnomalyLogRepository repository;

    @InjectMocks
    private SensorAnomalyLogService service;

    @Test
    @DisplayName("이상치 데이터 기록")
    void log_success() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 4000.0);
        String devEui = "devEui";
        int roomId = 10;
        ValidationStatus status = ValidationStatus.OUT_OF_RANGE;
        Instant time = Instant.now();

        service.log(data, devEui, roomId, status, time);

        ArgumentCaptor<SensorAnomalyLogDocument> captor = ArgumentCaptor.forClass(SensorAnomalyLogDocument.class);
        verify(repository, times(1)).save(captor.capture());

        SensorAnomalyLogDocument savedDoc = captor.getValue();
        assertEquals("co2", savedDoc.getMeasurement());
        assertEquals(4000.0, savedDoc.getValue());
        assertEquals(devEui, savedDoc.getDevEui());
        assertEquals(roomId, savedDoc.getRoomId());
        assertEquals(status, savedDoc.getStatus());
        assertEquals(time, savedDoc.getDetectedAt());
    }

    @Test
    @DisplayName("예외 발생 시 중단되지 않음")
    void log_fail() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 4000.0);
        doThrow(new RuntimeException("ES down")).when(repository).save(any());

        assertThatCode(() -> service.log(data, "devEui", 10, ValidationStatus.OUT_OF_RANGE, Instant.now()))
                .doesNotThrowAnyException();
    }
}
