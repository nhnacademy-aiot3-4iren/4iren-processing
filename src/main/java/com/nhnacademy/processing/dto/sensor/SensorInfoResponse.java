package com.nhnacademy.processing.dto.sensor;

import java.util.Map;

public record SensorInfoResponse (
        Integer roomId,
        String devEui,
        String deviceName,
        Map<String, String> measurement
) {
}
