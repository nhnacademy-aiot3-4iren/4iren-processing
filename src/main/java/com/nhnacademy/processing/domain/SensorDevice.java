package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sensor_devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SensorDevice {

    @Id
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
}
