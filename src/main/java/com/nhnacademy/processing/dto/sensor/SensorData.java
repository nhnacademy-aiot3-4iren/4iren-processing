package com.nhnacademy.processing.dto.sensor;

import com.nhnacademy.processing.dto.MeasurementCategory;

import java.time.Instant;

public record SensorData(
        MeasurementCategory category,
        String measurement,
        Double value,

        Instant measuredAt
) {}
