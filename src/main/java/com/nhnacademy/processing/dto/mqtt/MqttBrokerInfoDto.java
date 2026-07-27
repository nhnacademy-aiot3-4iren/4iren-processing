package com.nhnacademy.processing.dto.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;

public record MqttBrokerInfoDto(
        long id,
        String serverName,
        String brokerUrl,
        String username,
        String password,
        String topic
) {
    public static MqttBrokerInfoDto from(MqttBrokerInfo info) {
        return new MqttBrokerInfoDto(
                info.getId(),
                info.getServerName(),
                info.getBrokerUrl(),
                info.getUsername(),
                info.getPassword(),
                info.getTopic()
        );
    }
}
