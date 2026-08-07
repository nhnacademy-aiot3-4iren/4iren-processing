package com.nhnacademy.processing.service.alert;

import com.nhnacademy.processing.dto.alert.AlertEvent;
import com.nhnacademy.processing.dto.alert.AlertType;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.rule.Rule;
import com.nhnacademy.processing.service.validation.ValidationRuleRegistry;
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
    private final ValidationRuleRegistry validationRuleRegistry;

    @Value("${spring.rabbitmq.template.exchange}")
    private String exchange;
    @Value("${processing.rabbitmq.routing-key.anomaly}")
    private String routingKey;

    public void publish(DeviceIdentity device, String measurement, Double value, Instant detectedAt) {

        Rule rule = validationRuleRegistry.findRule(measurement).orElse(null);
        String unit = (rule != null) ? rule.measurement().unit() : "";

        Double threshold = null;
        if(rule != null) {
            threshold = (value > rule.maxValue()) ? rule.maxValue() : rule.minValue();
        }

        AlertEvent event = new AlertEvent(
                device.roomId(),
                device.point(),
                AlertType.SENSOR_ANOMALY.name(),
                "센서 이상치 탐지",
                device.devEui(),
                device.deviceName(),
                List.of(
                        new AlertEvent.NodeResult(
                                measurement,
                                unit,
                                threshold,
                                value
                        )
                ),
                detectedAt,
                UUID.randomUUID().toString()
        );

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (Exception e) {
            log.error("이상 알림 발행 실패 : roomId({}), devEui({}), measurement({})",
                    device.roomId(), device.devEui(), measurement, e);
        }
    }
}
