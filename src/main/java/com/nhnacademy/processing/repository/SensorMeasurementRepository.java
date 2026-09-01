package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SensorMeasurementRepository extends JpaRepository<SensorMeasurement, Long> {

    @Query("select sm from SensorMeasurement sm " +
            "join fetch sm.measurementType " +
            "where sm.sensorDevice.devEui = :devEui and sm.sensorDevice.mqttBrokerInfo.id = :brokerId")
    List<SensorMeasurement> findAllByDevEuiWithMeasurementType(@Param("devEui") String devEui, @Param("brokerId") Long brokerId);

    @Query("select sm from SensorMeasurement sm " +
            "join fetch sm.measurementType mt " +
            "join fetch mt.unit " +
            "join fetch sm.sensorDevice sd " +
            "where sm.enabled = true and sd.roomId = :roomId")
    List<SensorMeasurement> findAllActiveMeasurementsByRoomId(@Param("roomId") int roomId);

    @Query("SELECT sm " +
            "FROM SensorMeasurement sm " +
            "JOIN FETCH sm.measurementType mt " +
            "JOIN FETCH mt.unit mu " +
            "WHERE sm.sensorDevice.devEui = :devEui " +
            "AND sm.enabled = true")
    List<SensorMeasurement> findAllByDevEuiWithMetricTypeAndUnit(@Param("devEui") String devEui);

    @Query("SELECT sm " +
            "FROM SensorMeasurement sm " +
            "JOIN FETCH sm.sensorDevice sd " +
            "JOIN FETCH sm.measurementType mt " +
            "JOIN FETCH mt.unit mu " +
            "WHERE sd.devEui IN :devEuis " +
            "AND sm.enabled = true")
    List<SensorMeasurement> findAllByDevEuiInWithMetricTypeAndUnit(@Param("devEuis") List<String> devEuis);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SensorMeasurement sm WHERE sm.sensorDevice.id IN " +
            "(SELECT sd.id FROM SensorDevice sd WHERE sd.mqttBrokerInfo.id = :brokerId)")
    void deleteAllByBrokerId(@Param("brokerId") Long brokerId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SensorMeasurement sm WHERE sm.sensorDevice.id IN " +
            "(SELECT sd.id FROM SensorDevice sd WHERE sd.mqttBrokerInfo.buildingId = :buildingId)")
    void deleteAllByBuildingId(@Param("buildingId") Long buildingId);
}
