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
    @DisplayName("이상 감지 이벤트 정상 발행 - value가 maxValue보다 큼 (threshold = maxValue)")
    void publish_Success_AboveMax() {
        String measurement = "temperature";
        Double value = 99.9;
        Instant now = Instant.now();
        DeviceIdentity device = new DeviceIdentity(
                "app1", "appName", "prof1", "deviceName123", "devEui123", 101, "location", "pointA"
        );

        Metric metric = new Metric(1L, "temperature", "°C");
        Rule mockRule = new Rule(1L, metric, -10.0, 30.0);
        when(ruleRegistry.findRule(measurement)).thenReturn(Optional.of(mockRule));

        notificationPublisher.publish(device, measurement, value, now);

        ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(exchange), eq(routingKey), eventCaptor.capture());

        AlertEvent event = eventCaptor.getValue();
        assertEquals(30.0, event.nodeResults().getFirst().threshold()); // maxValue
    }

    // --- 신규 추가: threshold가 minValue에 맞춰 할당되는 브랜치 테스트 ---
    @Test
    @DisplayName("이상 감지 이벤트 정상 발행 - value가 minValue보다 작음 (threshold = minValue)")
    void publish_Success_BelowMin() {
        String measurement = "temperature";
        Double value = -20.0;
        Instant now = Instant.now();
        DeviceIdentity device = new DeviceIdentity(
                "app1", "appName", "prof1", "deviceName123", "devEui123", 101, "location", "pointA"
        );

        Metric metric = new Metric(1L, "temperature", "°C");
        Rule mockRule = new Rule(1L, metric, -10.0, 30.0);
        when(ruleRegistry.findRule(measurement)).thenReturn(Optional.of(mockRule));

        notificationPublisher.publish(device, measurement, value, now);

        ArgumentCaptor<AlertEvent> eventCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(exchange), eq(routingKey), eventCaptor.capture());

        AlertEvent event = eventCaptor.getValue();
        assertEquals(-10.0, event.nodeResults().getFirst().threshold()); // minValue
    }

    @Test
    @DisplayName("RabbitMQ 발행 실패해도 예외를 던지지 않고 로그만 남김")
    void publish_ThrowsException_LogsAndDoesNotThrow() {
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(AlertEvent.class));

        DeviceIdentity device = new DeviceIdentity(
                "app1", "appName", "prof1", "deviceName123", "dev123", 101, "location","pointA"
        );
        when(ruleRegistry.findRule(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> notificationPublisher.publish(device, "temperature", 50.0, Instant.now()));
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(AlertEvent.class));
    }
}