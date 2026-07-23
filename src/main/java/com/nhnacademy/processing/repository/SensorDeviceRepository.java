package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorDevice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, String> {
}
