package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "mqtt_broker_info",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mqtt_broker_info_building",
                        columnNames = {"building_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MqttBrokerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String serverName;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private String brokerUrl;

    @Column(length = 100)
    private String username;

    @Column(length = 255)
    private String password;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Boolean enabled;

    @OneToMany(mappedBy = "mqttBrokerInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SensorDevice> sensorDevices = new ArrayList<>();

    public MqttBrokerInfo(Long id, Long buildingId, String serverName, String brokerUrl, String username, String password, String topic) {
        this.id = id;
        this.buildingId = buildingId;
        this.serverName = serverName;
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.topic = topic;
        this.enabled = true;
    }


    public MqttBrokerInfo(Long buildingId, String serverName, String brokerUrl, String username, String password, String topic) {
        this.buildingId = buildingId;
        this.serverName = serverName;
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.topic = topic;
        this.enabled = true;
    }

    public void update(String serverName, String brokerUrl, String username, String password, String topic) {
        this.serverName = serverName;
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.topic = topic;
    }

    public void disable() {
        this.enabled = false;
    }
    public void enable() {
        this.enabled = true;
    }
}