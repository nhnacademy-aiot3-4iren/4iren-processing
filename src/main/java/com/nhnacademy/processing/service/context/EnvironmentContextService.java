package com.nhnacademy.processing.service.context;

import com.nhnacademy.processing.dto.context.EnvironmentContext;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentContextService {

    private static final String KEY_PREFIX = "env:context:";

    private final RedisTemplate<String, EnvironmentContext> environmentContextRedisTemplate;

    public Optional<EnvironmentContext> updateContext(ParsedSensorMessage message, Integer roomId) {
        if(roomId == null || roomId < 0) {
            return Optional.empty();
        }

        String devEui = message.device().devEui();

        List<SensorData> environmentData = message.sensorDataList().stream()
                .filter(data -> data.category() == MeasurementCategory.ENVIRONMENT)
                .toList();

        if(environmentData.isEmpty()) {
            return Optional.empty();
        }

        EnvironmentContext merged = merge(roomId, environmentData, devEui, message.measuredAt());
        save(roomId, merged);

        return Optional.of(merged);
    }

    private EnvironmentContext merge(Integer roomId, List<SensorData> environmentData, String devEui, Instant measuredAt) {
        EnvironmentContext existing = findExisting(roomId);

        Map<String, EnvironmentContext.MetricInfo> mergedMetrics = new HashMap<>();
        if(existing != null && existing.metrics() != null) {
            for(EnvironmentContext.MetricInfo metricInfo : existing.metrics()){
                mergedMetrics.put(metricInfo.metric(), metricInfo);
            }
        }

        Instant updatedAt = measuredAt != null ? measuredAt : Instant.now();
        for(SensorData data : environmentData) {
            mergedMetrics.put(data.measurement(), new EnvironmentContext.MetricInfo(data.measurement(), data.value(), devEui, updatedAt));
        }

        return new EnvironmentContext(roomId, List.copyOf(mergedMetrics.values()), updatedAt);
    }

    private void save(Integer roomId, EnvironmentContext context) {
        environmentContextRedisTemplate.opsForValue().set(key(roomId), context);
    }

    private EnvironmentContext findExisting(Integer roomId) {
        try {
            return environmentContextRedisTemplate.opsForValue().get(key(roomId));
        } catch (Exception e) {
            log.warn("EnvironmentContext 조회 실패. 신규 컨텍스트 대체: roomId({})", roomId, e);
            return null;
        }
    }

    private String key(Integer roomId) {
        return KEY_PREFIX + roomId;
    }
}
