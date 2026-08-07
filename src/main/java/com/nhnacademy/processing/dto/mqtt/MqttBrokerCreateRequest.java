package com.nhnacademy.processing.dto.mqtt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MqttBrokerCreateRequest(

        @NotNull @NotBlank
        String serverName,

        @NotNull @NotBlank
        String brokerUrl,


        String username,
        String password,

        @NotNull @NotBlank
        String topic
) {}