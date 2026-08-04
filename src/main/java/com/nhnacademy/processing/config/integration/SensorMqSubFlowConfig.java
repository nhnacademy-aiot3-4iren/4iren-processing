package com.nhnacademy.processing.config.integration;

import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.service.validation.SensorValidator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;

import java.util.List;

/**
 * MQ Sub-flow
 *
 * sensorPubSubChannel(ParsedSensorMessage)을 구독해서
 *   -> [Transformer + Filter] roomId를 모르거나 발행 대상 SensorData가 하나도 없으면 드랍
 *                              발행 대상만 걸러낸 새 ParsedSensorMessage 하나로 재구성.
 *   -> [AMQP Outbound Adapter] 위에서 만든 메시지 하나만 RabbitMQ로 발행
 *
 * 발행 대상 판단 기준:
 *   - DEVICE_HEALTH  : 항상 발행 대상
 *   - ENVIRONMENT    : SensorValidator.validate() 결과가 VALID일 때만 발행 대상
 *   - NETWORK_QUALITY: 발행 대상 아님
 */
@Configuration
public class SensorMqSubFlowConfig {

    @Bean
    public IntegrationFlow sensorMqSubFlow(SensorValidator sensorValidator,
                                           RabbitTemplate rabbitTemplate,
                                           @Value("${spring.rabbitmq.template.exchange}") String exchange,
                                           @Value("${spring.rabbitmq.template.routing-key}") String routingKey) {
        return IntegrationFlow.from("sensorPubSubChannel")
                .handle(ParsedSensorMessage.class, (parsed, headers) -> {
                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                    if(roomId == null || roomId < 0) {
                        return null;
                    }

                    List<SensorData> publishable = parsed.sensorDataList().stream()
                            .filter(data -> isPublishable(data, sensorValidator))
                            .toList();

                    if(publishable.isEmpty()) {
                        return null;
                    }

                    DeviceIdentity newDevice = new DeviceIdentity(
                            parsed.device().applicationId(),
                            parsed.device().applicationName(),
                            parsed.device().deviceProfileId(),
                            parsed.device().deviceName(),
                            parsed.device().devEui(),
                            null,
                            roomId
                    );
                    return new ParsedSensorMessage(newDevice, publishable, parsed.measuredAt());
                })
                .handle(Amqp.outboundAdapter(rabbitTemplate)
                        .exchangeName(exchange)
                        .routingKey(routingKey)
                )
                .get();
    }

    private static boolean isPublishable(SensorData data, SensorValidator sensorValidator) {
        return switch (data.category()) {
            case DEVICE_HEALTH -> true;
            case ENVIRONMENT -> sensorValidator.validate(data) == ValidationStatus.VALID;
            case NETWORK_QUALITY -> false;
        };
    }
}
