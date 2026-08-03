//package com.nhnacademy.processing.service.mqtt;
//
//import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
//import com.nhnacademy.processing.service.handler.SensorMessageHandler;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.integration.dsl.context.IntegrationFlowContext;
//
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class MqttBrokerRegistryUnitTest {
//
//    @Mock
//    private IntegrationFlowContext flowContext;
//    @Mock
//    private MqttBrokerService mqttBrokerService;
//    @Mock
//    private SensorMessageHandler sensorMessageHandler;
//    @Mock
//    private IntegrationFlowContext.IntegrationFlowRegistration registration;
//
//    private ExecutorService executor;
//    private MqttBrokerRegistry registry;
//
//    @BeforeEach
//    void setUp() {
//        executor = Executors.newSingleThreadExecutor();
//        registry = new MqttBrokerRegistry(flowContext, mqttBrokerService, executor, sensorMessageHandler);
//    }
//
//    @Test
//    @DisplayName("특정 브로터 등록 중 예외가 발생하더라도 중단되이 않음")
//    void registerBroker_Exception() {
//        MqttBrokerInfoDto brokenDto = new MqttBrokerInfoDto(1L, "serverName", "tcp://broken:1883", "username", "password", "topic/broken");
//        MqttBrokerInfoDto okDto = new MqttBrokerInfoDto(2L, "serverName", "tcp://ok:1883", "username", "password", "topic/ok");
//
//        when(mqttBrokerService.getMqttBrokerInfo()).thenReturn(List.of(brokenDto, okDto));
//
//        IntegrationFlowContext.IntegrationFlowRegistrationBuilder builderMock =
//                mock(IntegrationFlowContext.IntegrationFlowRegistrationBuilder.class);
//        when(builderMock.id(anyString())).thenReturn(builderMock);
//        when(builderMock.register()).thenReturn(registration);
//
//        when(flowContext.registration(any()))
//                .thenThrow(new RuntimeException("brokenDto 연결 실패 시뮬레이션"))
//                .thenReturn(builderMock);
//
//        registry.init();
//
//        verify(mqttBrokerService, times(1)).getMqttBrokerInfo();
//        verify(flowContext, times(2)).registration(any());
//    }
//}
