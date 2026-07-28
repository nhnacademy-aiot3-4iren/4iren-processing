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

    private final Set<String> knownDevices = ConcurrentHashMap.newKeySet();
    private final Cache<String, Set<String>> knownMeasurementsCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(6))
            .build();

    private final SensorDeviceService sensorDeviceService;

    public void ensureRegistered(ParsedSensorMessage message, Long brokerId, int roomId) {
        String devEui = message.device().devEui();

        if(!knownDevices.contains(devEui)) {
            sensorDeviceService.registerDeviceIfAbsent(message, devEui, brokerId, roomId);
            knownDevices.add(devEui);
        }

        registerMeasurementsIfAbsent(message, devEui);
    }

    private void registerMeasurementsIfAbsent(ParsedSensorMessage message, String devEui) {
        Set<String> known = knownMeasurementsCache.get(devEui, sensorDeviceService::loadKnownMeasurements);

        message.sensorDataList().stream()
                .filter(data -> data.category() == MeasurementCategory.ENVIRONMENT)
                .filter(data -> !known.contains(data.measurement()))
                .forEach(data -> sensorDeviceService.registerMeasurement(devEui, data, known));
    }
}
