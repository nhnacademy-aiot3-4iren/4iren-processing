package com.nhnacademy.processing.dto;

public record SensorContext(
        String devEui,
        Long roomId,
        Long teamId
) {
}
