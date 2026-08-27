package com.nhnacademy.processing.dto.rule;

import com.nhnacademy.processing.domain.SensorValidationRule;

public record Rule(
        long id,
        Metric measurement,
        double minValue,
        double maxValue
) {
    public static Rule from(SensorValidationRule rule) {
        return new Rule(rule.getId(),
                Metric.from(rule.getMeasurementType()),
                rule.getMinValue(),
                rule.getMaxValue() );
    }

    public boolean isInRange(double value) {
        return minValue <= value && maxValue >= value;
    }
}
