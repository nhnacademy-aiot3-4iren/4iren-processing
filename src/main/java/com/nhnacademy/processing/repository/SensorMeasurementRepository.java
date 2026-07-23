package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorMeasurementRepository extends JpaRepository<SensorMeasurement, Long> {
}
