package com.nhnacademy.processing.dto.alert;

import java.time.Instant;
import java.util.List;

public record AlertEvent (
        Long roomId,
        String alertType,
        String alertTitle,

        List<MetricViolationDto> metricViolations,

        Instant detectedAt,
        String eventId
) {
    public record MetricViolationDto (
            String deviceEui,
            String measurementType,
            Double value
    ) {}
}
