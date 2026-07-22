package com.nhnacademy.processing.dto.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;

public record MqttBrokerDto (
        long id,
        String serverName,
        String brokerUrl,
        String username,
        String password,
        String topic
) {
    public static MqttBrokerDto from(MqttBrokerInfo info) {
        return new MqttBrokerDto(
                info.getId(),
                info.getServerName(),
                info.getBrokerUrl(),
                info.getUsername(),
                info.getPassword(),
                info.getTopic()
        );
    }
}
