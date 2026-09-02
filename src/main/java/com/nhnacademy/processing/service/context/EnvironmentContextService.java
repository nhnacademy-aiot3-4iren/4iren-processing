package com.nhnacademy.processing.service.context;

import com.nhnacademy.processing.dto.context.EnvironmentContext;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentContextService {

    private static final String KEY_PREFIX = "env:context:";
    private static final String WRONGTYPE_PREFIX = "WRONGTYPE";

    /**
     * env:context:{roomId} 키의 TTL(분).
     * - 마지막 갱신 후 일정 시간 센서 데이터가 없으면 컨텍스트를 자동 만료시켜 stale 데이터를 방지한다.
     * - 부수 효과로, 어떤 이유로든 키가 Hash가 아닌 다른 타입으로 오염되더라도 영구히 남지 않고
     *   TTL 경과 후 사라지므로 WRONGTYPE 장애가 무한정 지속되는 것을 막아준다.
     */
    @Value("${processing.environment-context.ttl-minutes:30}")
    private long ttlMinutes;

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

        // 1. Hash 필드 갱신 (HMSET / HSET) + TTL 갱신. WRONGTYPE이면 자동 복구 후 1회 재시도.
        putAllWithRecovery(key, updates, true);

        // 2. 현재 방의 전체 메트릭 조회 후 EnvironmentContext 반환
        Map<String, EnvironmentContext.MetricInfo> allEntries =
                environmentContextRedisTemplate.<String, EnvironmentContext.MetricInfo>opsForHash().entries(key);

        List<EnvironmentContext.MetricInfo> metrics = new ArrayList<>(allEntries.values());
        return Optional.of(new EnvironmentContext(roomId, metrics, updatedAt));
    }

    /**
     * HMSET 시도. 대상 키가 Hash가 아닌 다른 타입으로 오염되어 WRONGTYPE이 발생하면
     * 해당 키를 삭제하고 정확히 한 번만 재시도한다. 그 외 예외(연결 실패, 타임아웃 등)는
     * 복구 대상이 아니므로 그대로 호출부로 전파해서 sensorErrorChannel로 라우팅되게 한다.
     */
    private void putAllWithRecovery(String key, Map<String, EnvironmentContext.MetricInfo> updates, boolean allowRetry) {
        try {
            environmentContextRedisTemplate.<String, EnvironmentContext.MetricInfo>opsForHash().putAll(key, updates);
            environmentContextRedisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        } catch (RedisSystemException e) {
            if (allowRetry && isWrongType(e)) {
                log.warn("Redis key({})가 Hash 타입이 아니라 WRONGTYPE 발생. 키를 삭제하고 1회 재시도합니다.", key);
                environmentContextRedisTemplate.delete(key);
                putAllWithRecovery(key, updates, false);
                return;
            }
            throw e;
        }
    }

    /**
     * 예외 체인을 따라가며 근본 원인이 Redis의 WRONGTYPE 에러인지 판별한다.
     */
    private boolean isWrongType(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RedisCommandExecutionException
                    && cause.getMessage() != null
                    && cause.getMessage().startsWith(WRONGTYPE_PREFIX)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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