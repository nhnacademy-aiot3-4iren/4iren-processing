package com.nhnacademy.processing.dto.sensor;

public record SensorData(
        MeasurementCategory category,
        String measurement,
        Double value
) {}
