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
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentContextService {

    private static final String KEY_PREFIX = "env:context:";
    private final RedisTemplate<String, EnvironmentContext.MetricInfo> environmentContextRedisTemplate;

    /**
     * 센서 수신 데이터를 해당 RoomId의 Redis Hash에 원자적으로 저장/갱신
     */
    public Optional<EnvironmentContext> updateContext(ParsedSensorMessage message, Integer roomId) {
        if (roomId == null || message.device() == null) {
            return Optional.empty();
        }

        String devEui = message.device().devEui();
        Instant updatedAt = message.measuredAt() != null ? message.measuredAt() : Instant.now();

        List<SensorData> environmentData = message.sensorDataList().stream()
                .filter(data -> data.category() == MeasurementCategory.ENVIRONMENT || data.category() == MeasurementCategory.DEVICE_HEALTH)
                .toList();

        if (environmentData.isEmpty()) {
            return Optional.empty();
        }

        String key = key(roomId);
        Map<String, EnvironmentContext.MetricInfo> updates = new HashMap<>();
        for (SensorData data : environmentData) {
            updates.put(data.measurement(), new EnvironmentContext.MetricInfo(data.measurement(), data.value(), devEui, updatedAt));
        }

        // 1. 락 없이 원자적으로 Hash 필드 갱신 (HMSET / HSET)
        environmentContextRedisTemplate.<String, EnvironmentContext.MetricInfo>opsForHash().putAll(key, updates);

        // 2. 현재 방의 전체 메트릭 조회 후 EnvironmentContext 반환
        Map<String, EnvironmentContext.MetricInfo> allEntries =
                environmentContextRedisTemplate.<String, EnvironmentContext.MetricInfo>opsForHash().entries(key);

        List<EnvironmentContext.MetricInfo> metrics = new ArrayList<>(allEntries.values());
        return Optional.of(new EnvironmentContext(roomId, metrics, updatedAt));
    }

    /**
     * 특정 방의 현재 전체 환경 컨텍스트 조회
     */
    public Optional<EnvironmentContext> getContext(Integer roomId) {
        if (roomId == null) {
            return Optional.empty();
        }

        String key = key(roomId);
        Map<String, EnvironmentContext.MetricInfo> entries =
                environmentContextRedisTemplate.<String, EnvironmentContext.MetricInfo>opsForHash().entries(key);

        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        List<EnvironmentContext.MetricInfo> metrics = new ArrayList<>(entries.values());
        Instant latestUpdatedAt = metrics.stream()
                .map(EnvironmentContext.MetricInfo::updatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return Optional.of(new EnvironmentContext(roomId, metrics, latestUpdatedAt));
    }

    private String key(Integer roomId) {
        return KEY_PREFIX + roomId;
    }
}