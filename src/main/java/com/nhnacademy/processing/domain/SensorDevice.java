package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "sensor_devices",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sensor_device_deveui_broker",
                        columnNames = {"dev_eui", "broker_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SensorDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String devEui;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id", nullable = false)
    private MqttBrokerInfo mqttBrokerInfo;

    @Column(length = 36, nullable = false)
    private String applicationId;

    @Column(length = 100, nullable = false)
    private String applicationName;

    @Column(length = 36, nullable = false)
    private String deviceProfileId;

    @Column(length = 100, nullable = false)
    private String deviceName;

    @Column
    private Integer roomId;

    @Column(length = 100)
    private String location;

    @Column(length = 50)
    private String point;

    public SensorDevice(String devEui, MqttBrokerInfo mqttBrokerInfo, String applicationId, String applicationName,
                        String deviceProfileId, String deviceName, Integer roomId, String location, String point) {
        this.devEui = devEui;
        this.mqttBrokerInfo = mqttBrokerInfo;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.deviceProfileId = deviceProfileId;
        this.deviceName = deviceName;
        this.roomId = roomId;
        this.location = location;
        this.point = point;
    }

    public void assignRoom(Integer roomId) {
        this.roomId = roomId;
    }
}
