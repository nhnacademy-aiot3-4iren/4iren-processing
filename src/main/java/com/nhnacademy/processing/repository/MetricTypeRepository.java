package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MetricTypeRepository extends JpaRepository<MetricType, Long> {
    Optional<MetricType> findByCode(String code);

    @Query("SELECT mt FROM MetricType mt JOIN FETCH mt.unit mu")
    List<MetricType> findAllWithUnit();
}
