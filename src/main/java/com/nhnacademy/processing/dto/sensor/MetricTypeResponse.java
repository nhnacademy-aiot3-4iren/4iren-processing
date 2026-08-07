package com.nhnacademy.processing.dto.sensor;

import com.nhnacademy.processing.domain.MeasurementUnit;
import com.nhnacademy.processing.domain.MetricType;
import com.nhnacademy.processing.domain.SensorMeasurement;

public record MetricTypeResponse(
        String metricCode,
        String displayName,
        String metricKind,
        String status,
        String description,

        String ucumCode,
        String unitDisplayName,
        String symbol
) {
    public static MetricTypeResponse from(SensorMeasurement measurement) {
        MetricType mt = measurement.getMeasurementType();
        return from(mt);
    }

    public static MetricTypeResponse from(MetricType mt) {
        MeasurementUnit mu = mt.getUnit();

        return new MetricTypeResponse(
                mt.getCode(),
                mt.getDisplayName(),
                mt.getKind().name(),
                mt.getStatus().name(),
                mt.getDescription(),
                mu.getUcumCode(),
                mu.getDisplayName(),
                mu.getSymbol()
        );
    }
}
