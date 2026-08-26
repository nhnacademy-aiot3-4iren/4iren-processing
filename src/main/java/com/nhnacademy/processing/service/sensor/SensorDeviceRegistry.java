package com.nhnacademy.processing.service.sensor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nhnacademy.processing.domain.SensorDevice;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.sensor.RoomAssignmentResult;
import com.nhnacademy.processing.dto.sensor.SensorRoomAssignmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDeviceRegistry {

    private final ConcurrentHashMap<String, Boolean> knownDevices = new ConcurrentHashMap<>();

    // 측정항목 메타데이터 캐시
    private final Cache<String, Set<String>> knownMeasurementsCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(6))
            .build();

    // devEui@brokerId -> roomId 캐시 (Optional.empty()로 미배정 상태도 캐싱)
    private final Cache<String, Optional<Integer>> roomIdCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    private final SensorDeviceService sensorDeviceService;

    public void ensureRegistered(ParsedSensorMessage message, Long brokerId) {
        String devEui = message.device().devEui();
        String cacheKey = getCacheKey(devEui, brokerId);

        knownDevices.computeIfAbsent(cacheKey, key -> {
            sensorDeviceService.registerDeviceIfAbsent(message, devEui, brokerId);
            return Boolean.TRUE;
        });

        registerMeasurementsIfAbsent(message, devEui, brokerId, cacheKey);
    }

    // devEui와 brokerId로 배정된 roomId를 캐시/DB에서 조회
    public Integer resolveRoomId(String devEui, Long brokerId) {
        if (devEui == null || brokerId == null) {
            return null;
        }
        String cacheKey = getCacheKey(devEui, brokerId);
        Optional<Integer> roomIdOpt = roomIdCache.get(cacheKey, key ->
                Optional.ofNullable(sensorDeviceService.findRoomId(devEui, brokerId))
        );
        return (roomIdOpt != null && roomIdOpt.isPresent()) ? roomIdOpt.get() : null;
    }

    // 관리자가 방을 배정했을 때 즉시 캐시 갱신/무효화
    public void assignRoomsAndEvictCache(List<SensorRoomAssignmentRequest> requests) {
        List<RoomAssignmentResult> updatedDevices = sensorDeviceService.assignRooms(requests);
        for (RoomAssignmentResult result : updatedDevices) {
            String cacheKey = getCacheKey(result.devEui(), result.brokerId());
            roomIdCache.put(cacheKey, Optional.ofNullable(result.roomId()));
        }
    }

    private void registerMeasurementsIfAbsent(ParsedSensorMessage message, String devEui, Long brokerId, String cacheKey) {
        Set<String> known = knownMeasurementsCache.get(cacheKey, key -> sensorDeviceService.loadKnownMeasurements(devEui, brokerId));
        message.sensorDataList().stream()
                .filter(data -> data.category() == MeasurementCategory.ENVIRONMENT)
                .filter(data -> !known.contains(data.measurement()))
                .forEach(data -> sensorDeviceService.registerMeasurement(devEui, brokerId, data, known));
    }

    private String getCacheKey(String devEui, Long brokerId) {
        return devEui + "@" + brokerId;
    }
}