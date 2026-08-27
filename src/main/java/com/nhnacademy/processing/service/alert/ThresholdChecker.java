package com.nhnacademy.processing.service.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdChecker {

    private static final String NAMESPACE = "oor";

    private final AnomalyCounter anomalyCounter;

    @Value("${processing.alert.out-of-range.threshold}")
    private int threshold;

    public boolean shouldAlert(String devEui, String measurement) {
        String key = counterKey(devEui, measurement);
        try {
            long count = anomalyCounter.incrementAndGet(NAMESPACE, key);
            if (count < threshold) {
                return false;
            }

            return anomalyCounter.tryMarkAlerted(NAMESPACE, key);
        } catch (Exception e) {
            log.error("Threshold 판단 실패, 알림 스킵: devEui({}), measurement({})",
                    devEui, measurement, e);
            return false;
        }
    }

    private String counterKey(String devEui, String measurement) {
        return devEui + ":" + measurement;
    }
}
