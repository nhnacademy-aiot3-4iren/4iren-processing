package com.nhnacademy.processing.dto.alert;

import java.time.Instant;
import java.util.List;

public record AlertEvent (
        Integer roomId,
        String point,
        String alertType,
        String alertTitle,
        String deviceEui,
        String deviceName,

        List<NodeResult> nodeResults,

        Instant detectedAt,
        String eventId
) {
    public record NodeResult (
            String metricType,
            String unit,
            Double threshold,
            Double value
    ) {}
}
