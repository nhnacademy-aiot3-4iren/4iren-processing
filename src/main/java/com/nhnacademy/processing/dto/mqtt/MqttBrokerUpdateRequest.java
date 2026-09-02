package com.nhnacademy.processing.dto.mqtt;

import jakarta.validation.constraints.NotBlank;

public record MqttBrokerUpdateRequest(
        @NotBlank(message = "서버 이름은 필수입니다.")
        String serverName,

        @NotBlank(message = "브로커 URL은 필수입니다.")
        String brokerUrl,

        String username,
        String password,

        @NotBlank(message = "토픽은 필수입니다.")
        String topic
) {}