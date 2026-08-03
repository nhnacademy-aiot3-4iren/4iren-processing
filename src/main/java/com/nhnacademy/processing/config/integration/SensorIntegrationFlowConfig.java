package com.nhnacademy.processing.config.integration;

import com.nhnacademy.processing.service.handler.SensorMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageHandler;

/**
 * 센서 처리 파이프라인 SI 진입 Flow
 * MqttBrokerRegistry -> sensorInputChannel -> 이 Flow -> SensorMessageHandler
 */
@Slf4j
@Configuration
public class SensorIntegrationFlowConfig {

    @Bean
    public IntegrationFlow sensorProcessingFlow(SensorMessageHandler sensorMessageHandler) {
        return IntegrationFlow.from("sensorInputChannel")
                .wireTap("sensorLoggingChannel")
                .handle((MessageHandler) message -> {
                    Long brokerId = message.getHeaders().get(SensorMessageHeaders.BROKER_ID, Long.class);
                    sensorMessageHandler.handle(brokerId, message);
                })
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
