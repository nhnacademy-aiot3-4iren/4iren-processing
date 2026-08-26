package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, Long> {
    List<SensorDevice> findAllByRoomId(Integer roomId);

    List<SensorDevice> findAllByMqttBrokerInfo_BuildingId(Long buildingId);

    List<SensorDevice> findAllByMqttBrokerInfo_BuildingIdAndRoomId(Long buildingId, Integer roomId);

    boolean existsByDevEuiAndMqttBrokerInfo_Id(String devEui, Long brokerId);

    Optional<SensorDevice> findByDevEuiAndMqttBrokerInfo_Id(String devEui, Long brokerId);

    Optional<SensorDevice> findByDevEuiAndMqttBrokerInfo_BuildingId(String devEui, Long buildingId);
}
