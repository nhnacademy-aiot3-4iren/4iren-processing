package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorMeasurementRepository extends JpaRepository<SensorMeasurement, Long> {
    List<SensorMeasurement> findAllBySensorDevice_DevEui(String sensorDeviceDevEui);
}
