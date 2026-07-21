package com.nhnacademy.processing.dto.sensor;

public record SensorTags(
        String applicationId,
        String devEui,
        String deviceName,
        String roomId
) {
}
