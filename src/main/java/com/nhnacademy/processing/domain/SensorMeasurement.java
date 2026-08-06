package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "sensor_measurements",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sensor_measurement",
                        columnNames = {"dev_eui", "measurement_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SensorMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dev_eui", nullable = false)
    private SensorDevice sensorDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_id", nullable = false)
    private MetricType measurementType;

    @Column(nullable = false)
    private Boolean enabled;

    public SensorMeasurement(SensorDevice sensorDevice, MetricType measurementType) {
        this.sensorDevice = sensorDevice;
        this.measurementType = measurementType;
        this.enabled = true;
    }

    public void enable() {
        this.enabled = true;
    }
    public void disable() {
        this.enabled = false;
    }
}