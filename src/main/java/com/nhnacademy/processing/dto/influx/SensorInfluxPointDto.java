package com.nhnacademy.processing.dto.influx;

import java.time.Instant;

public record SensorInfluxPointDto(
        String measurement,
        double value,
        Instant measuredAt,
        String applicationId,
        String devEui,
        String deviceName,
        String roomId
) {
}
