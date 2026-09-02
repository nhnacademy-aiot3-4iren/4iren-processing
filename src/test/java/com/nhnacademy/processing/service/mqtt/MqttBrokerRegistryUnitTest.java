package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.exception.MqttBrokerConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.messaging.MessageChannel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttBrokerRegistryUnitTest {

    @Mock private IntegrationFlowContext flowContext;
    @Mock private MqttBrokerService mqttBrokerService;
    @Mock private MessageChannel sensorInputChannel;
    @Mock private MessageChannel sensorErrorChannel;
    @Mock private IntegrationFlowContext.IntegrationFlowRegistration registration;

    private ExecutorService executor;
    private MqttBrokerRegistry registry;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        registry = new MqttBrokerRegistry(flowContext, mqttBrokerService, executor, sensorInputChannel, sensorErrorChannel);
    }

    @Test
    @DisplayName("초기화 시 브로커 등록 실패(Exception)하더라도 진행된다")
    void init_registerBroker_Exception_Continues() {
        MqttBrokerInfoDto brokenDto = new MqttBrokerInfoDto(1L, 101L, "serverName", "tcp://broken:1883", "username", "password", "topic/broken");
        MqttBrokerInfoDto okDto = new MqttBrokerInfoDto(2L, 102L, "serverName", "tcp://ok:1883", "username", "password", "topic/ok");

        when(mqttBrokerService.getMqttBrokerInfo()).thenReturn(List.of(brokenDto, okDto));

        IntegrationFlowContext.IntegrationFlowRegistrationBuilder builderMock =
                mock(IntegrationFlowContext.IntegrationFlowRegistrationBuilder.class);
        when(builderMock.id(anyString())).thenReturn(builderMock);
        when(builderMock.register()).thenReturn(registration);

        when(flowContext.registration(any()))
                .thenThrow(new RuntimeException("brokenDto 연결 실패"))
                .thenReturn(builderMock);

        registry.init();

        verify(mqttBrokerService, times(1)).getMqttBrokerInfo();
        verify(flowContext, times(2)).registration(any()); // 에러 나도 다음 브로커 등록(okDto) 시도됨
    }

    @Test
    @DisplayName("단일 브로커 등록 중 예외 발생 시 MqttBrokerConnectionException을 던진다")
    void registerBroker_ThrowsMqttBrokerConnectionException() {
        MqttBrokerInfoDto brokenDto = new MqttBrokerInfoDto(1L, 101L, "serverName", "tcp://broken:1883", "username", "password", "topic");

        when(flowContext.registration(any())).thenThrow(new RuntimeException("Connection Refused"));

        assertThatThrownBy(() -> registry.registerBroker(brokenDto))
                .isInstanceOf(MqttBrokerConnectionException.class)
                .hasMessageContaining("tcp://broken:1883");
    }
}