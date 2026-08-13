package com.nhnacademy.processing.service.alert;

import com.nhnacademy.processing.dto.alert.AlertEvent;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.rule.Metric;
import com.nhnacademy.processing.dto.rule.Rule;
import com.nhnacademy.processing.service.validation.ValidationRuleRegistry;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ValidationRuleRegistry ruleRegistry;

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
        String measurement = "temperature";
        Double value = 99.9;
        Instant now = Instant.now();

        DeviceIdentity device = new DeviceIdentity(
                "app1", "appName", "prof1", "deviceName123", "devEui123", 101, "pointA"
        );
        Metric metric = new Metric(1L, "temperature", "°C");
        Rule mockRule = new Rule(1L, metric, -10.0, 30.0);

        when(ruleRegistry.findRule(measurement)).thenReturn(Optional.of(mockRule));
        notificationPublisher.publish(device, measurement, value, now);

        ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(exchange), eq(routingKey), eventCaptor.capture());

        AlertEvent event = eventCaptor.getValue();
        assertEquals(101, event.roomId());
        assertEquals("pointA", event.point());
        assertEquals("deviceName123", event.deviceName());
        assertEquals("devEui123", event.deviceEui());
        assertEquals("SENSOR_ANOMALY", event.alertType());
        assertEquals(now, event.detectedAt());
        assertNotNull(event.eventId());

        assertEquals(1, event.nodeResults().size());
        AlertEvent.NodeResult nodeResult = event.nodeResults().getFirst();
        assertEquals(measurement, nodeResult.metricType());
        assertEquals("°C", nodeResult.unit());
        assertEquals(30.0, nodeResult.threshold());
        assertEquals(value, nodeResult.value());
    }

    @Test
    @DisplayName("RabbitMQ 통신 에러 발생 시 예외를 전파하지 않고 로그만 남김")
    void publish_ThrowsException_LogsAndDoesNotThrow() {
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(AlertEvent.class));

        DeviceIdentity device = new DeviceIdentity(
                "app1", "appName", "prof1", "deviceName123", "dev123", 101, "pointA"
        );

        when(ruleRegistry.findRule(anyString())).thenReturn(Optional.empty());
        notificationPublisher.publish(device, "temperature", 50.0, Instant.now());

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(AlertEvent.class));
    }
}