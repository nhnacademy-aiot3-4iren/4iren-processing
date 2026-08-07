package com.nhnacademy.processing.dto.mqtt;

public record MqttBrokerCreateRequest(
        String serverName,
        String brokerUrl,
        String username,
        String password,
        String topic
) {}