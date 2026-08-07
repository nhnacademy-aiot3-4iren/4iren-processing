package com.nhnacademy.processing.dto.rule;

import com.nhnacademy.processing.domain.MetricType;

public record Metric(
        long id,
        String name,
        String unit
) {
    public static Metric from(MetricType measurementType) {
        return new Metric(
                measurementType.getId(),
                measurementType.getCode(),
                measurementType.getUnit().getSymbol()
        );
    }
}
