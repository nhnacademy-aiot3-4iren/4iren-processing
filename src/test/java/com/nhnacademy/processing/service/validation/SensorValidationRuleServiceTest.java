package com.nhnacademy.processing.service.validation;

import com.nhnacademy.processing.domain.MeasurementUnit;
import com.nhnacademy.processing.domain.MetricKind;
import com.nhnacademy.processing.domain.MetricType;
import com.nhnacademy.processing.domain.MetricTypeStatus;
import com.nhnacademy.processing.domain.SensorValidationRule;
import com.nhnacademy.processing.dto.rule.Rule;
import com.nhnacademy.processing.repository.SensorValidationRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorValidationRuleServiceTest {

    @Mock
    private SensorValidationRuleRepository ruleRepository;

    @InjectMocks
    private SensorValidationRuleService ruleService;

    @Test
    @DisplayName("모든 검증 규칙을 조회하여 Map 형태로 반환")
    void getRule_Success() {
        MeasurementUnit unit1 = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType type1 = new MetricType(1L, unit1, "co2", "이산화탄소 농도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2 설명");
        SensorValidationRule rule1 = new SensorValidationRule(1L, type1, 0.0, 1000.0);

        MeasurementUnit unit2 = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType type2 = new MetricType(2L, unit2, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "temp 설명");
        SensorValidationRule rule2 = new SensorValidationRule(2L, type2, -10.0, 40.0);

        when(ruleRepository.findAllWithMetricTypeAndUnit()).thenReturn(List.of(rule1, rule2));

        Map<String, Rule> rules = ruleService.getRule();

        assertThat(rules).hasSize(2);
        assertThat(rules.get("co2").minValue()).isEqualTo(0.0);
        assertThat(rules.get("co2").maxValue()).isEqualTo(1000.0);
        assertThat(rules.get("temperature").minValue()).isEqualTo(-10.0);
    }

    @Test
    @DisplayName("존재하는 규칙 ID로 범위를 성공적으로 업데이트")
    void updateRule_Success() {
        MeasurementUnit unit = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType type = new MetricType(1L, unit, "co2", "이산화탄소 농도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2 설명");
        SensorValidationRule rule = new SensorValidationRule(1L, type, 0.0, 50.0);

        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

        ruleService.updateRule(1L, 10.0, 100.0);

        assertThat(rule.getMinValue()).isEqualTo(10.0);
        assertThat(rule.getMaxValue()).isEqualTo(100.0);
        verify(ruleRepository, times(1)).save(rule);
    }

    @Test
    @DisplayName("존재하지 않는 규칙 ID로 업데이트를 시도하면 예외 발생")
    void updateRule_NotFound_ThrowsException() {
        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ruleService.updateRule(99L, 10.0, 100.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(ruleRepository, never()).save(any());
    }
}