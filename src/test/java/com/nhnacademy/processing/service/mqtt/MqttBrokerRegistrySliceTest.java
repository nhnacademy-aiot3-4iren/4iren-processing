//package com.nhnacademy.processing.service.mqtt;
//
//import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
//import com.nhnacademy.processing.service.handler.SensorMessageHandler;
//import org.eclipse.paho.client.mqttv3.MqttClient;
//import org.eclipse.paho.client.mqttv3.MqttException;
//import org.eclipse.paho.client.mqttv3.MqttMessage;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
//import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.integration.config.EnableIntegration;
//import org.springframework.messaging.Message;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.util.List;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.*;
//
//@Testcontainers
//@SpringBootTest(classes = {MqttBrokerRegistry.class, MqttBrokerRegistrySliceTest.TestExecutorConfig.class})
//@ImportAutoConfiguration(IntegrationAutoConfiguration.class)
//@EnableIntegration
//class MqttBrokerRegistrySliceTest {
//
//    @Container
//    static GenericContainer<?> mosquitto = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
//            .withExposedPorts(1883)
//            .withCommand("sh", "-c", "echo 'listener 1883\nallow_anonymous true' > /mosquitto/config/mosquitto.conf && mosquitto -c /mosquitto/config/mosquitto.conf")
//            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());
//
//    @MockitoBean private MqttBrokerService mqttBrokerService;
//    @MockitoBean private SensorMessageHandler sensorMessageHandler;
//
//    @Autowired private MqttBrokerRegistry registry;
//
//    @TestConfiguration
//    static class TestExecutorConfig {
//        @Bean
//        public ExecutorService mqttProcessingExecutor() {
//            return Executors.newFixedThreadPool(2);
//        }
//    }
//
//    private MqttClient testPublisher;
//    private String brokerUrl;
//
//    @BeforeEach
//    void setUp() throws MqttException {
//        when(mqttBrokerService.getMqttBrokerInfo()).thenReturn(List.of());
//
//        brokerUrl = "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
//        testPublisher = new MqttClient(brokerUrl, MqttClient.generateClientId());
//        testPublisher.connect();
//    }
//
//    @AfterEach
//    void tearDown() throws MqttException {
//        if (testPublisher.isConnected()) {
//            testPublisher.disconnect();
//        }
//        testPublisher.close();
//    }
//
//    @Test
//    @DisplayName("동적으로 등록한 브로커를 통해 들어온 메시지가 정상적으로 핸들러로 전달")
//    void handleMessage_Success() throws MqttException {
//        MqttBrokerInfoDto info = sampleDto(1L, brokerUrl, "test/topic/#");
//        registry.registerBroker(info);
//
//        testPublisher.publish("test/topic/1", new MqttMessage("hello-payload".getBytes()));
//
//        verify(sensorMessageHandler, timeout(5000)).handle(eq(1L), argThat(msg -> {
//            String payload = String.valueOf(msg.getPayload());
//            return payload.equals("hello-payload");
//        }));
//    }
//
//    @Test
//    @DisplayName("브로커 연결 해제 후에는 메시지가 핸들러로 전달되지 않음")
//    void unregisterBroker_StopsMessageDelivery() throws Exception {
//        MqttBrokerInfoDto info = sampleDto(2L, brokerUrl, "test/unregister/#");
//        registry.registerBroker(info);
//
//        registry.unregisterBroker(2L);
//
//        testPublisher.publish("test/unregister/1", new MqttMessage("should-not-arrive".getBytes()));
//
//        verify(sensorMessageHandler, after(2000).never()).handle(any(), any());
//    }
//
//    @Test
//    @DisplayName("핸들러에서 메시지 처리 중 예외가 발생해도 다음 메시지 수신에는 영향을 주지 않음")
//    void handleMessage_ExceptionDoesNotKillConsumer() throws Exception {
//        MqttBrokerInfoDto info = sampleDto(3L, brokerUrl, "test/error/#");
//
//        doThrow(new RuntimeException("핸들러 내부 처리 실패"))
//                .doNothing()
//                .when(sensorMessageHandler).handle(eq(3L), any(Message.class));
//
//        registry.registerBroker(info);
//
//        testPublisher.publish("test/error/1", new MqttMessage("first".getBytes()));
//        testPublisher.publish("test/error/2", new MqttMessage("second".getBytes()));
//
//        verify(sensorMessageHandler, timeout(5000).times(2)).handle(eq(3L), any(Message.class));
//    }
//
//    private MqttBrokerInfoDto sampleDto(Long id, String url, String topic) {
//        MqttBrokerInfoDto dto = mock(MqttBrokerInfoDto.class);
//        when(dto.id()).thenReturn(id);
//        when(dto.brokerUrl()).thenReturn(url);
//        when(dto.topic()).thenReturn(topic);
//        when(dto.username()).thenReturn(null);
//        return dto;
//    }
//}