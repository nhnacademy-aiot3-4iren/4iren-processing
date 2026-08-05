package com.nhnacademy.processing.service.alert;

import com.nhnacademy.processing.dto.alert.AlertEvent;
import com.nhnacademy.processing.dto.alert.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.template.exchange}")
    private String exchange;
    @Value("${processing.rabbitmq.routing-key.anomaly}")
    private String routingKey;

    public void publish(Long roomId, String devEui, String measurement, Double value, Instant detectedAt) {
        AlertEvent event = new AlertEvent(
                roomId,
                AlertType.SENSOR_ANOMALY.name(),
                "센서 이상",
                List.of(new AlertEvent.MetricViolationDto(
                        devEui,
                        measurement,
                        value
                )),
                detectedAt,
                "P" + UUID.randomUUID()
        );

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (Exception e) {
            log.error("알림 발행 실패: roomId({}), devEui({}), measurement({})", roomId, devEui, measurement, e);
        }
    }
}
