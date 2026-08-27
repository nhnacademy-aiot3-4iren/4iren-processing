package com.nhnacademy.processing.service.validation;

import com.nhnacademy.processing.dto.rule.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationRuleRegistryTest {

    @Mock
    private SensorValidationRuleService ruleService;

    @InjectMocks
    private ValidationRuleRegistry ruleRegistry;

    @Test
    @DisplayName("캐시 갱신 시 서비스에서 최신 규칙을 불러와 캐시에 저장")
    void refresh_Success() {
        Rule co2Rule = new Rule(1L, null, 0.0, 800.0);
        Map<String, Rule> mockRules = Map.of("co2", co2Rule);

        when(ruleService.getRule()).thenReturn(mockRules);

        ruleRegistry.refresh();
        Optional<Rule> foundRule = ruleRegistry.findRule("co2");

        assertThat(foundRule).isPresent();
        assertThat(foundRule.get().minValue()).isEqualTo(0.0);
        assertThat(foundRule.get().maxValue()).isEqualTo(800.0);
    }

    @Test
    @DisplayName("캐시 갱신 중 예외가 발생하면 기존 캐시 상태를 유지")
    void refresh_Exception() {
        Rule oldRule = new Rule(1L, null, 0.0, 800.0);
        when(ruleService.getRule()).thenReturn(Map.of("co2", oldRule));
        ruleRegistry.refresh();

        when(ruleService.getRule()).thenThrow(new RuntimeException("DB Connection Error"));

        ruleRegistry.refresh();
        Optional<Rule> foundRule = ruleRegistry.findRule("co2");

        assertThat(foundRule).isPresent();
        assertThat(foundRule.get().maxValue()).isEqualTo(800.0);
    }

    @Test
    @DisplayName("규칙 업데이트하면 서비스 계층의 업데이트를 호출, 캐시 즉시 갱신")
    void update_And_Refresh() {
        Rule updatedRule = new Rule(1L, null, 10.0, 1500.0);
        when(ruleService.getRule()).thenReturn(Map.of("co2", updatedRule));

        ruleRegistry.update(1L, 10.0, 1500.0);

        verify(ruleService, times(1)).updateRule(1L, 10.0, 1500.0);
        verify(ruleService, times(1)).getRule();

        Optional<Rule> foundRule = ruleRegistry.findRule("co2");
        assertThat(foundRule).isPresent();
        assertThat(foundRule.get().minValue()).isEqualTo(10.0);
    }
}