package com.nhnacademy.processing.dto.sensor;

public record SensorContext(
        String devEui,
        Long roomId,
        Long teamId
) {
}
