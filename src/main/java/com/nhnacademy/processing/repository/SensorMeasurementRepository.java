package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SensorMeasurementRepository extends JpaRepository<SensorMeasurement, Long> {
    @Query("select sm from SensorMeasurement sm " +
            "join fetch sm.measurementType " +
            "where sm.sensorDevice.devEui = :devEui")
    List<SensorMeasurement> findAllByDevEuiWithMeasurementType(@Param("devEui") String devEui);

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
}
