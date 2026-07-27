package com.nhnacademy.processing.service.validation;

import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SensorValidator {

    private final ValidationRuleRegistry ruleRegistry;

    public ValidationStatus validate(SensorData data) {
        return ruleRegistry.findRule(data.measurement())
                .map(rule -> rule.isInRange(data.value()) ? ValidationStatus.VALID : ValidationStatus.OUT_OF_RANGE)
                .orElse(ValidationStatus.NO_RULE_DEFINED);
    }
}
