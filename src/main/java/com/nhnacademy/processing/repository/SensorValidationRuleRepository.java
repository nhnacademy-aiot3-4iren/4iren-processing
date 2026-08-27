package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SensorValidationRuleRepository extends JpaRepository<SensorValidationRule, Long> {
    @Query("SELECT r FROM SensorValidationRule r " +
            "JOIN FETCH r.measurementType mt " +
            "JOIN FETCH mt.unit")
    List<SensorValidationRule> findAllWithMetricTypeAndUnit();
}
