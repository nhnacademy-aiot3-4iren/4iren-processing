package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.MeasurementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeasurementTypeRepository extends JpaRepository<MeasurementType, Long> {
    Optional<MeasurementType> findByName(String name);
}
