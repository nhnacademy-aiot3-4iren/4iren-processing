package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, String> {
    List<SensorDevice> findAllByRoomId(Integer roomId);

//    List<SensorDevice> findAllByMqttBrokerInfo_Id(Long brokerId);

    List<SensorDevice> findAllByMqttBrokerInfo_BuildingId(Long buildingId);

    boolean existsByDevEuiAndMqttBrokerInfo_Id(String devEui, Long brokerId);

    Optional<SensorDevice> findByDevEuiAndMqttBrokerInfo_Id(String devEui, Long brokerId);
}
