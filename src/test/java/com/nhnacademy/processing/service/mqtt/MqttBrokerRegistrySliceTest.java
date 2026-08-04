package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.config.integration.SensorMessageHeaders;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes = {MqttBrokerRegistry.class, MqttBrokerRegistrySliceTest.TestExecutorConfig.class})
@ImportAutoConfiguration(IntegrationAutoConfiguration.class)
@EnableIntegration
class MqttBrokerRegistrySliceTest {

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
            .withExposedPorts(1883)
            .withCommand("sh", "-c", "echo 'listener 1883\nallow_anonymous true' > /mosquitto/config/mosquitto.conf && mosquitto -c /mosquitto/config/mosquitto.conf")
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    @MockitoBean private MqttBrokerService mqttBrokerService;

    @MockitoBean(name = "sensorInputChannel")
    private MessageChannel sensorInputChannel;
    @MockitoBean(name = "sensorErrorChannel")
    private MessageChannel sensorErrorChannel;

    @Autowired private MqttBrokerRegistry registry;

    @TestConfiguration
    static class TestExecutorConfig {
        @Bean
        public ExecutorService mqttProcessingExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    }

    private MqttClient testPublisher;
    private String brokerUrl;

    @BeforeEach
    void setUp() throws MqttException {
        when(mqttBrokerService.getMqttBrokerInfo()).thenReturn(List.of());

        brokerUrl = "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883);
        testPublisher = new MqttClient(brokerUrl, MqttClient.generateClientId());
        testPublisher.connect();
    }

    @AfterEach
    void tearDown() throws MqttException {
        if (testPublisher.isConnected()) {
            testPublisher.disconnect();
        }
        testPublisher.close();
    }

    @Test
    @DisplayName("동적으로 등록한 브로커를 통해 메시지 수신 시 Header에 brokerId를 담아 sensorInputChannel로 전달")
    void handleMessage_Success() throws MqttException {
        MqttBrokerInfoDto info = sampleDto(1L, brokerUrl, "test/topic/#");
        registry.registerBroker(info);

        testPublisher.publish("test/topic/1", new MqttMessage("hello-payload".getBytes()));

        verify(sensorInputChannel, timeout(5000)).send(argThat(msg -> {
            String payload = String.valueOf(msg.getPayload());
            Long brokerId = msg.getHeaders().get(SensorMessageHeaders.BROKER_ID, Long.class);
            return payload.equals("hello-payload") && brokerId != null && brokerId.equals(1L);
        }));
    }

    @Test
    @DisplayName("브로커 연결 해제 후에는 더 이상 메시지를 수신하지 않음")
    void unregisterBroker_StopsMessageDelivery() throws Exception {
        MqttBrokerInfoDto info = sampleDto(2L, brokerUrl, "test/unregister/#");
        registry.registerBroker(info);

        registry.unregisterBroker(2L);
        testPublisher.publish("test/unregister/1", new MqttMessage("should-not-arrive".getBytes()));

        verify(sensorInputChannel, after(2000).never()).send(any(Message.class));
    }

    @Test
    @DisplayName("핸들러에서 메시지 처리 중 예외 발생 시 sensorErrorChannel로 전달. 중단되지 않음")
    void handleMessage_ExceptionDoesNotKillConsumer() throws Exception {
        MqttBrokerInfoDto info = sampleDto(3L, brokerUrl, "test/error/#");

        doThrow(new RuntimeException("채널 전송 실패"))
                .doReturn(true)
                .when(sensorInputChannel).send(any(Message.class));

        registry.registerBroker(info);
        testPublisher.publish("test/error/1", new MqttMessage("first".getBytes()));
        testPublisher.publish("test/error/2", new MqttMessage("second".getBytes()));

        verify(sensorInputChannel, timeout(5000).times(2)).send(any(Message.class));
        verify(sensorErrorChannel, timeout(5000).atLeastOnce()).send(any(Message.class));
    }

    private MqttBrokerInfoDto sampleDto(Long id, String url, String topic) {
        MqttBrokerInfoDto dto = mock(MqttBrokerInfoDto.class);
        when(dto.id()).thenReturn(id);
        when(dto.brokerUrl()).thenReturn(url);
        when(dto.topic()).thenReturn(topic);
        when(dto.username()).thenReturn(null);
        return dto;
    }
}