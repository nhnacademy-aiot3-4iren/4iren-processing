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
            long count = anomalyCounter.increment(NAMESPACE, key);
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

    public void resetOnRecovery(String devEui, String measurement) {
        try {
            anomalyCounter.reset(NAMESPACE, counterKey(devEui, measurement));
        } catch (Exception e) {
            log.error("Threshold 카운터 리셋 실패 (다음 TTL 만료 때 자연 정리됨): devEui({}), measurement({})",
                    devEui, measurement, e);
        }
    }

    private String counterKey(String devEui, String measurement) {
        return devEui + ":" + measurement;
    }
}
