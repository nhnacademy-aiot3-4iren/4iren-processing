package com.nhnacademy.processing.service.es;

import com.nhnacademy.processing.domain.SensorAnomalyLogDocument;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.repository.SensorAnomalyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorAnomalyLogService {

    private final SensorAnomalyLogRepository repository;

    public void log(SensorData data, String devEui, Integer roomId, ValidationStatus status, Instant time) {
        try {
            SensorAnomalyLogDocument document = new SensorAnomalyLogDocument(data.measurement(), data.value(), devEui, roomId, status, time);
            repository.save(document);
        } catch (Exception e) {
            log.error("ES 이상 로그 저장 실패: measurement({}), devEui({}), status({})", data.measurement(), devEui, status, e);
        }
    }
}
