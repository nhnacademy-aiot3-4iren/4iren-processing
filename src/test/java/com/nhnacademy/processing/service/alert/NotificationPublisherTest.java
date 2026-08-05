package com.nhnacademy.processing.service.alert;

import com.nhnacademy.processing.dto.alert.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationPublisher notificationPublisher;

    private final String exchange = "test.exchange";
    private final String routingKey = "test.anomaly.key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationPublisher, "exchange", exchange);
        ReflectionTestUtils.setField(notificationPublisher, "routingKey", routingKey);
    }

    @Test
    @DisplayName("정상적으로 RabbitMQ 발행한다")
    void publish_Success() {
        Integer roomId = 101;
        String devEui = "devEui123";
        String measurement = "temperature";
        Double value = 99.9;
        Instant now = Instant.now();

        notificationPublisher.publish(roomId, devEui, measurement, value, now);

        ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(exchange), eq(routingKey), eventCaptor.capture());

        AlertEvent event = eventCaptor.getValue();
        assertEquals(roomId, event.roomId());
        assertEquals("SENSOR_ANOMALY", event.alertType());
        assertEquals(now, event.detectedAt());
        assertNotNull(event.eventId());

        assertEquals(1, event.metricViolations().size());
        assertEquals(devEui, event.metricViolations().getFirst().deviceEui());
        assertEquals(measurement, event.metricViolations().getFirst().measurementType());
        assertEquals(value, event.metricViolations().getFirst().value());
    }

    @Test
    @DisplayName("RabbitMQ 통신 에러 발생 시 예외를 전파하지 않고 로그만 남김")
    void publish_ThrowsException_LogsAndDoesNotThrow() {
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(AlertEvent.class));

        notificationPublisher.publish(101, "dev123", "temperature", 50.0, Instant.now());

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(AlertEvent.class));
    }
}