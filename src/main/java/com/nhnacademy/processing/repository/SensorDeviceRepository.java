package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, Long> {
    List<SensorDevice> findAllByRoomId(Integer roomId);

    List<SensorDevice> findAllByMqttBrokerInfo_BuildingId(Long buildingId);

    List<SensorDevice> findAllByMqttBrokerInfo_BuildingIdAndRoomId(Long buildingId, Integer roomId);

    boolean existsByDevEuiAndMqttBrokerInfo_Id(String devEui, Long brokerId);

    Optional<SensorDevice> findByDevEuiAndMqttBrokerInfo_Id(String devEui, Long brokerId);

    Optional<SensorDevice> findByDevEuiAndMqttBrokerInfo_BuildingId(String devEui, Long buildingId);

    @Query("SELECT sd.roomId FROM SensorDevice sd WHERE sd.devEui = :devEui AND sd.mqttBrokerInfo.id = :brokerId")
    Optional<Integer> findRoomIdOnly(@Param("devEui") String devEui, @Param("brokerId") Long brokerId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SensorDevice sd WHERE sd.mqttBrokerInfo.id = :brokerId")
    void deleteAllByBrokerId(@Param("brokerId") Long brokerId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SensorDevice sd WHERE sd.mqttBrokerInfo.buildingId = :buildingId")
    void deleteAllByBuildingId(@Param("buildingId") Long buildingId);
}