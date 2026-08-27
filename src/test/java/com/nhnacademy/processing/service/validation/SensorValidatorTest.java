package com.nhnacademy.processing.service.validation;

import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.rule.Rule;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorValidatorTest {

    @Mock
    private ValidationRuleRegistry registry;

    @InjectMocks
    private SensorValidator validator;

    @Test
    @DisplayName("센서 데이터가 규칙 범위 내에 있으면 VALID 반환")
    void validate_Valid() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0);
        Rule rule = new Rule(1L, null, 0.0, 30.0);

        when(registry.findRule("temperature")).thenReturn(Optional.of(rule));

        ValidationStatus status = validator.validate(data);

        assertThat(status).isEqualTo(ValidationStatus.VALID);
    }

    @Test
    @DisplayName("센서 데이터가 규칙 범위를 벗어나면 OUT_OF_RANGE반환한다")
    void validate_OutOfRange() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 35.0);
        Rule rule = new Rule(1L, null, 0.0, 30.0);

        when(registry.findRule("temperature")).thenReturn(Optional.of(rule));

        ValidationStatus status = validator.validate(data);

        assertThat(status).isEqualTo(ValidationStatus.OUT_OF_RANGE);
    }

    @Test
    @DisplayName("해당 측정 항목에 대한 규칙이 등록되어 있지 않으면 NO_RULE_DEFINED 반환한다")
    void validate_NoRuleDefined() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "unknown_sensor", 10.0);

        when(registry.findRule("unknown_sensor")).thenReturn(Optional.empty());

        ValidationStatus status = validator.validate(data);

        assertThat(status).isEqualTo(ValidationStatus.NO_RULE_DEFINED);
    }
}
