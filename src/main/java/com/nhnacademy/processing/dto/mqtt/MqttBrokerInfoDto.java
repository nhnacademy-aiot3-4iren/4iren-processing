package com.nhnacademy.processing.dto.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;

public record MqttBrokerInfoDto(
        Long id,
        Long buildingId,
        String serverName,
        String brokerUrl,
        String username,
        String password,
        String topic
) {
    public static MqttBrokerInfoDto from(MqttBrokerInfo info) {
        return new MqttBrokerInfoDto(
                info.getId(),
                info.getBuildingId(),
                info.getServerName(),
                info.getBrokerUrl(),
                info.getUsername(),
                info.getPassword(),
                info.getTopic()
        );
    }

    public MqttBrokerUpdateRequest toUpdateRequest() {
        return new MqttBrokerUpdateRequest(
                this.serverName,
                this.brokerUrl,
                this.username,
                this.password,
                this.topic
        );
    }
}