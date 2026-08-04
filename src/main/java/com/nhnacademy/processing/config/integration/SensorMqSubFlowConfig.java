package com.nhnacademy.processing.config.integration;

import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.service.handler.SensorMessageHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageHandler;

/**
 * MQ Sub-flow
 *
 * sensorPubSubChannel(ParsedSensorMessage)을 구독해서 RabbitMQ 발행을 담당한다.
 */
@Configuration
public class SensorMqSubFlowConfig {

    @Bean
    public IntegrationFlow sensorMqSubFlow(SensorMessageHandler sensorMessageHandler) {
        return IntegrationFlow.from("sensorPubSubChannel")
                .handle((MessageHandler) message -> {
                    ParsedSensorMessage parsed = (ParsedSensorMessage) message.getPayload();
                    Integer roomId = message.getHeaders().get(SensorMessageHeaders.ROOM_ID, Integer.class);
                    sensorMessageHandler.publishAll(parsed, roomId);
                })
                .get();
    }
}
