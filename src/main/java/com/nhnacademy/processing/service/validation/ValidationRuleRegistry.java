package com.nhnacademy.processing.service.validation;

import com.nhnacademy.processing.dto.rule.Rule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationRuleRegistry {

    private final AtomicReference<Map<String, Rule>> cache = new AtomicReference<>(Map.of());
    private final SensorValidationRuleService ruleService;

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        try {
            Map<String, Rule> updated = ruleService.getRule();
            cache.set(updated);
            log.info("ValidationRule 캐시 갱신 완료: {}건", updated.size());
        } catch (Exception e) {
            log.error("ValidationRule 캐시 갱신 실패, 기존 캐시 유지", e);
        }
    }

    public Optional<Rule> findRule(String measurementName) {
        return Optional.ofNullable(cache.get().get(measurementName));
    }

    public void update(Long ruleId, double minValue, double maxValue) {
        ruleService.updateRule(ruleId, minValue, maxValue);
        refresh();
    }
}
