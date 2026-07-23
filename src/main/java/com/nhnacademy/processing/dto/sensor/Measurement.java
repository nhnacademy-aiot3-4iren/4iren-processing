package com.nhnacademy.processing.dto.sensor;

import com.nhnacademy.processing.domain.MeasurementType;

public record Measurement(
        long id,
        String name,
        String unit
) {
    public static Measurement from(MeasurementType measurementType) {
        return new Measurement(measurementType.getId(),
                measurementType.getName(),
                measurementType.getUnit().getSymbol());
    }
}
