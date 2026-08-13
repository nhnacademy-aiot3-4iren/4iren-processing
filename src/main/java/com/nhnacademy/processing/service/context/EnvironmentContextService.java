package com.nhnacademy.processing.service.context;

import com.nhnacademy.processing.dto.context.EnvironmentContext;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentContextService {

    private static final String KEY_PREFIX = "env:context:";
    private static final int MAX_RETRY = 3;

    private final RedisTemplate<String, EnvironmentContext> environmentContextRedisTemplate;

    public Optional<EnvironmentContext> updateContext(ParsedSensorMessage message, Integer roomId) {
        if (roomId == null || roomId < 0 || message.device() == null) {
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

        EnvironmentContext merged = updateWithOptimisticLock(roomId, environmentData, devEui, updatedAt);

        return Optional.of(merged);
    }

    private EnvironmentContext updateWithOptimisticLock(Integer roomId, List<SensorData> environmentData, String devEui, Instant updatedAt) {
        String key = key(roomId);

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            EnvironmentContext result = tryUpdateOnce(key, roomId, environmentData, devEui, updatedAt);
            if (result != null) {
                return result;
            }
            log.debug("EnvironmentContext 동시 갱신 충돌로 재시도: roomId({}), attempt({})", roomId, attempt);
        }

        throw new IllegalStateException(
                "EnvironmentContext 갱신 재시도 초과 (동시 갱신 충돌): roomId=" + roomId + ", retry=" + MAX_RETRY);
    }

    @SuppressWarnings("unchecked")
    private EnvironmentContext tryUpdateOnce(String key, Integer roomId, List<SensorData> environmentData, String devEui, Instant updatedAt) {
        AtomicReference<EnvironmentContext> mergedRef = new AtomicReference<>();

        List<Object> execResult = environmentContextRedisTemplate.execute(new SessionCallback<>() {
            @Override
            public List<Object> execute(@NonNull RedisOperations operations) {
                operations.watch(key);

                EnvironmentContext existing = (EnvironmentContext) operations.opsForValue().get(key);
                EnvironmentContext merged = merge(roomId, existing, environmentData, devEui, updatedAt);
                mergedRef.set(merged);

                operations.multi();
                operations.opsForValue().set(key, merged);

                return operations.exec();
            }
        });

        if (execResult == null || execResult.isEmpty()) {
            return null;
        }

        return mergedRef.get();
    }

    private EnvironmentContext merge(Integer roomId, EnvironmentContext existing, List<SensorData> environmentData, String devEui, Instant updatedAt) {
        Map<String, EnvironmentContext.MetricInfo> mergedMetrics = new HashMap<>();
        if (existing != null && existing.metrics() != null) {
            for (EnvironmentContext.MetricInfo metricInfo : existing.metrics()) {
                mergedMetrics.put(metricInfo.metric(), metricInfo);
            }
        }

        for (SensorData data : environmentData) {
            mergedMetrics.put(data.measurement(), new EnvironmentContext.MetricInfo(data.measurement(), data.value(), devEui, updatedAt));
        }

        return new EnvironmentContext(roomId, List.copyOf(mergedMetrics.values()), updatedAt);
    }

    private String key(Integer roomId) {
        return KEY_PREFIX + roomId;
    }
}
