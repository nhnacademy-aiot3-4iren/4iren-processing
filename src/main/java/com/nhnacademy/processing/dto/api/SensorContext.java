package com.nhnacademy.processing.dto.api;

public record SensorContext(
        String devEui,
        Long roomId,
        Long teamId
) {
}
