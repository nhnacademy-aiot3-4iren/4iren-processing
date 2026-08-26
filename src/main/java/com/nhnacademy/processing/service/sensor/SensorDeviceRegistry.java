package com.nhnacademy.processing.service.sensor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDeviceRegistry {

    private final ConcurrentHashMap<String, Boolean> knownDevices = new ConcurrentHashMap<>();
    private final Cache<String, Set<String>> knownMeasurementsCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(6))
            .build();

    private final SensorDeviceService sensorDeviceService;

    public void ensureRegistered(ParsedSensorMessage message, Long brokerId) {
        String devEui = message.device().devEui();
        String cacheKey = devEui + "@" + brokerId;

        knownDevices.computeIfAbsent(cacheKey, key -> {
            sensorDeviceService.registerDeviceIfAbsent(message, devEui, brokerId);
            return Boolean.TRUE;
        });

        registerMeasurementsIfAbsent(message, devEui, brokerId, cacheKey);
    }

    private void registerMeasurementsIfAbsent(ParsedSensorMessage message, String devEui, Long brokerId, String cacheKey) {
        Set<String> known = knownMeasurementsCache.get(cacheKey, key -> sensorDeviceService.loadKnownMeasurements(devEui, brokerId));

        message.sensorDataList().stream()
                .filter(data -> data.category() == MeasurementCategory.ENVIRONMENT)
                .filter(data -> !known.contains(data.measurement()))
                .forEach(data -> sensorDeviceService.registerMeasurement(devEui, brokerId, data, known));
    }
}
