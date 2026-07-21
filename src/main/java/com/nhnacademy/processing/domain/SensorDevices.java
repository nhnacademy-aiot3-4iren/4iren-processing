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
public class SensorDevices {

    @Id
    @Column(nullable = false, length = 16)
    private String devEui;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id")
    private MqttBrokerInfo mqttBrokerInfo;

    private String applicationId;

    private String applicationName;

    private String deviceProfileId;

    private String deviceName;

    private Integer roomId;
}
