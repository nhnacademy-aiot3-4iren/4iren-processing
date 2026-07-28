package com.nhnacademy.processing.service.rabbitmq;

import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitMqPublisherTest {

    @Mock
    private RabbitTemplate template;

    @InjectMocks
    private RabbitMqPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "sensor.exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "sensor.routingKey");
    }

    @Test
    @DisplayName("메시지 발행 시 지정된 설정값으로 1회 호출")
    void publish_success() {
        ParsedSensorMessage message = new ParsedSensorMessage(
                new DeviceIdentity("applicationId", "applicationName", "deviceId", "deviceName", "devEui", null, 10),
                List.of(),
                Instant.now()
        );

        publisher.publish(message);

        verify(template, times(1)).convertAndSend("sensor.exchange", "sensor.routingKey", message);
    }

    @Test
    @DisplayName("예외 발생 시 중단되지 않음")
    void publish_fail() {
        doThrow(new RuntimeException("RabbitMQ down")).when(template).convertAndSend(anyString(), anyString(), any(Object.class));

        ParsedSensorMessage message = new ParsedSensorMessage(
                new DeviceIdentity("applicationId", "applicationName", "deviceId", "deviceName", "devEui", null, 10),
                List.of(),
                Instant.now()
        );


        assertThatCode(() -> publisher.publish(message))
                .doesNotThrowAnyException();
    }
}
