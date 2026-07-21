package com.nhnacademy.processing.dto.sensor;

import com.nhnacademy.processing.dto.MeasurementCategory;

public record SensorData(
        MeasurementCategory category,
        String measurement,
        Double value
) {}
