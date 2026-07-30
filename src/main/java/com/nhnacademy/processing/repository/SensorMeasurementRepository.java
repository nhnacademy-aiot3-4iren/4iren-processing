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
            "join fetch sm.measurementType " +
            "join fetch sm.sensorDevice sd " +
            "where sm.enabled = true and sd.roomId = :roomId")
    List<SensorMeasurement> findAllActiveMeasurementsByRoomId(@Param("roomId") int roomId);
}
