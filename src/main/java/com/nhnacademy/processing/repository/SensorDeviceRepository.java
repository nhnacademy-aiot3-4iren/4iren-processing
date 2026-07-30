package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, String> {
    List<SensorDevice> findAllByRoomId(Integer roomId);
}
