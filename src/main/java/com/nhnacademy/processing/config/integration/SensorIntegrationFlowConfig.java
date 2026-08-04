package com.nhnacademy.processing.config.integration;

import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.service.converter.SensorPayloadConverter;
import com.nhnacademy.processing.service.process.SensorContextResolver;
import com.nhnacademy.processing.service.sensor.SensorDeviceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 센서 처리 파이프라인 SI 진입 Flow
 *
 * MqttBrokerRegistry -> sensorInputChannel
 *   -> [Transformer]      Raw String Payload -> ParsedSensorMessage    (SensorPayloadConverter)
 *   -> [Header Enricher]  devEui -> roomId 조회 후 헤더에 세팅             (SensorContextResolver, 캐시 그대로 유지)
 *   -> [Service Activator] 신규 기기/측정항목 등록, 통과만 시킴               (SensorDeviceRegistry)
 *   -> [Header Enricher]  원본 ParsedSensorMessage를 헤더로 복사           (Splitter 대비)
 *   -> sensorPubSubChannel (DB 저장 / RabbitMQ 발행으로 fan-out)

 */
@Slf4j
@Configuration
public class SensorIntegrationFlowConfig {

    @Bean
    public IntegrationFlow sensorProcessingFlow(SensorPayloadConverter payloadConverter,
                                                SensorContextResolver contextResolver,
                                                SensorDeviceRegistry sensorDeviceRegistry,
                                                MessageChannel sensorErrorChannel) {
        return IntegrationFlow.from("sensorInputChannel")
                .wireTap("sensorLoggingChannel")

                // 1. Transformer: String -> ParsedSensorMessage (brokerId 헤더는 자동으로 유지됨)
                .transform(payloadConverter, "convert")

                // 2. Header Enricher: devEui로 roomId 조회
                .enrichHeaders(h -> h.headerFunction(SensorMessageHeaders.ROOM_ID, message -> {
                    ParsedSensorMessage parsed = (ParsedSensorMessage) message.getPayload();
                    String devEui = parsed.device().devEui();
                    SensorContext context = contextResolver.resolve(devEui)
                            .orElseGet(() -> new SensorContext(devEui, -1, -1));
                    return context.roomId();
                }))

                // 3. Service Activator: 신규 기기/측정항목 등록. 등록 실패는 sensorErrorChannel로 보내고 통과
                .handle(ParsedSensorMessage.class, (payload, headers) -> {
                    Long brokerId = headers.get(SensorMessageHeaders.BROKER_ID, Long.class);
                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                    try {
                        sensorDeviceRegistry.ensureRegistered(payload, brokerId, roomId);
                    } catch (Exception e) {
                        sensorErrorChannel.send(MessageBuilder.withPayload(new SensorErrorFlowConfig.ProcessingFailure(brokerId, e)).build());
                    }
                    return payload;
                })

                // 4. Header Enricher: DB Sub-flow의 Splitter 이후에도 device/measuredAt 정보를 쓸 수 있도록 원본 ParsedSensorMessage을 헤더로 복사
                .enrichHeaders(h -> h.headerFunction(SensorMessageHeaders.PARSED_MESSAGE, Message::getPayload))

                // 5. DB 저장/ RabbitMQ 발행으로 fan-out
                .channel("sensorPubSubChannel")
                .get();
    }

    @Bean
    public IntegrationFlow sensorLoggingFlow() {
        return IntegrationFlow.from("sensorLoggingChannel")
                .handle((payload, headers) -> {
                    log.debug("[WireTap] brokerId={}, payloadType={}",
                            headers.get(SensorMessageHeaders.BROKER_ID),
                            payload != null ? payload.getClass().getSimpleName() : "null");
                    return null;
                })
                .get();
    }
}
