package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mqtt_broker_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MqttBrokerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String serverName;

    @Column(nullable = false)
    private String brokerUrl;

    @Column(length = 100)
    private String username;

    private String password;    // todo: 암호화가 필요할거 같은데 아직 잘 모르겠음

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Boolean enabled;

    public MqttBrokerInfo(Long id, String serverName, String brokerUrl, String username, String password, String topic) {
        this.id = id;
        this.serverName = serverName;
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.topic = topic;
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
    public void enable() {
        this.enabled = true;
    }
}