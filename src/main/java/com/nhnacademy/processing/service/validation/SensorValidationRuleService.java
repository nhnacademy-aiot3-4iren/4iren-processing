package com.nhnacademy.processing.service.validation;

import com.nhnacademy.processing.domain.SensorValidationRule;
import com.nhnacademy.processing.dto.rule.Rule;
import com.nhnacademy.processing.repository.SensorValidationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensorValidationRuleService {

    private final SensorValidationRuleRepository ruleRepository;
    private final ValidationRuleRegistry validationRuleRegistry;

    @Transactional(readOnly = true)
    public Map<String, Rule> getRule() {
        return ruleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        rule -> rule.getMeasurementType().getName(),
                        Rule::from
                ));
    }

    @Transactional
    public void updateRule(Long ruleId, double minValue, double maxValue) {
        SensorValidationRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new IllegalArgumentException("규칙 없음: " + ruleId));

        rule.update(minValue, maxValue);
        ruleRepository.save(rule);
    }
}
