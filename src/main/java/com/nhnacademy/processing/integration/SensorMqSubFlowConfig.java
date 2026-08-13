package com.nhnacademy.processing.integration;

import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.service.context.EnvironmentContextService;
import com.nhnacademy.processing.service.validation.SensorValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;

/**
 * MQ Sub-flow
 *
 * sensorPubSubChannel(ParsedSensorMessage)을 구독해서
 *   -> [Transformer + Filter] roomId를 모르거나 발행 대상 SensorData가 하나도 없으면 드랍
 *                              발행 대상만 걸러낸 새 ParsedSensorMessage 하나로 재구성.
 *   -> [Service Activator] EnvironmentContextService로 roomId의 Redis 환경 컨텍스트 갱신
 *                           (ENVIRONMENT 카테고리만 병합 대상. 실패해도 파이프라인은 계속 진행하고
 *                            에러만 sensorErrorChannel로 라우팅)
 *   -> [AMQP Outbound Adapter] 위에서 만든 메시지 하나만 RabbitMQ로 발행
 *
 * 발행 대상 판단 기준:
 *   - DEVICE_HEALTH  : 항상 발행 대상
 *   - ENVIRONMENT    : SensorValidator.validate() 결과가 VALID일 때만 발행 대상
 *   - NETWORK_QUALITY: 발행 대상 아님
 */
@Slf4j
@Configuration
public class SensorMqSubFlowConfig {

    @Bean
    public IntegrationFlow sensorMqSubFlow(SensorValidator sensorValidator,
                                           RabbitTemplate rabbitTemplate,
                                           EnvironmentContextService environmentContextService,
                                           @Value("${spring.rabbitmq.template.exchange}") String exchange,
                                           @Value("${processing.rabbitmq.routing-key.normal}") String routingKey,
                                           MessageChannel sensorErrorChannel) {
        // 1. PubSub 채널에서 복사본 수신
        return IntegrationFlow.from("sensorPubSubChannel")

                // 2. 유효한 데이터만 추려서 새 ParsedSensorMessage 생성
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
                            roomId,
                            parsed.device().point()
                    );
                    return new ParsedSensorMessage(newDevice, publishable, parsed.measuredAt());
                })

                // 3. roomId의 Redis 환경 컨텍스트 갱신
                .handle(ParsedSensorMessage.class, (message, headers) -> {
                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                    Long brokerId = headers.get(SensorMessageHeaders.BROKER_ID, Long.class);

                    try {
                        environmentContextService.updateContext(message, roomId);
                    } catch (Exception e) {
                        log.error("EnvironmentContext Redis 갱신 실패: roomId({}), devEui({})", roomId, message.device().devEui(), e);
                        sensorErrorChannel.send(MessageBuilder.withPayload(new SensorErrorFlowConfig.ProcessingFailure(brokerId, e)).build());
                    }

                    return message;
                })

                // 4. AMQP Outbound Adapter로 RabbitMQ 발행
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
