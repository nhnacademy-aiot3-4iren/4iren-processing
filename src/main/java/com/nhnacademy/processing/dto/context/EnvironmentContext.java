package com.nhnacademy.processing.dto.context;

import java.time.Instant;
import java.util.List;

public record EnvironmentContext (
        Integer roomId,
        List<MetricInfo> metrics,
        Instant updatedAt
) {
    public record MetricInfo(
            String metric,
            Long value,
            String detectedDeviceEui,
            Instant updatedAt
    ) {}

    public static EnvironmentContext empty(Integer roomId) {
        return new EnvironmentContext(roomId, List.of(), null);
    }
}