package com.nhnacademy.processing.dto.parse;

import com.nhnacademy.processing.dto.rule.MeasurementCategory;

public record SensorData(
        MeasurementCategory category,
        String measurement,
        Double value
) {}
