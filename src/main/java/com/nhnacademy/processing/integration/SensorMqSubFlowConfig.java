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
 *                           (ENVIRONMENT/DEVICE_HEALTH 카테고리만 병합 대상. 갱신 결과인
 *                            EnvironmentContext(roomId, metrics, updatedAt)로 페이로드를 교체한다.
 *                            갱신에 실패하거나 갱신 대상이 없으면 메시지를 드랍하고,
 *                            실패 시 에러만 sensorErrorChannel로 라우팅)
 *   -> [AMQP Outbound Adapter] EnvironmentContext 페이로드를 RabbitMQ로 발행
 *                              (rule-engine의 SensorPayloadConverter가 roomId/metrics/updatedAt
 *                               형태로 역직렬화하므로, ParsedSensorMessage를 그대로 흘려보내면 안 된다)
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
                    if(roomId == null) {
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
                            parsed.device().location(),
                            parsed.device().point()
                    );
                    return new ParsedSensorMessage(newDevice, publishable, parsed.measuredAt());
                })

                // 3. roomId의 Redis 환경 컨텍스트 갱신 -> 갱신 결과 EnvironmentContext로 페이로드 교체
                .handle(ParsedSensorMessage.class, (message, headers) -> {
                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                    Long brokerId = headers.get(SensorMessageHeaders.BROKER_ID, Long.class);

                    try {
                        return environmentContextService.updateContext(message, roomId).orElse(null);
                    } catch (Exception e) {
                        try {
                            sensorErrorChannel.send(MessageBuilder.withPayload(
                                    new SensorErrorFlowConfig.ProcessingFailure(brokerId, roomId, message.device().devEui(), e)).build());
                        } catch (Exception channelEx) {
                            log.error("EnvironmentContext 갱신 및 에러 채널 전송 동시 실패: brokerId({}), roomId({}), devEui({})",
                                    brokerId, roomId, message.device().devEui(), e);
                            log.error("에러 채널 전송 실패 원인:", channelEx);
                        }
                        // 갱신 실패 시 발행할 유효한 EnvironmentContext가 없으므로 드랍
                        return null;
                    }
                })

                // 4. AMQP Outbound Adapter로 EnvironmentContext 발행
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