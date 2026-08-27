package com.nhnacademy.processing.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;

@Slf4j
@Configuration
public class SensorErrorFlowConfig {

    @Bean
    public IntegrationFlow sensorErrorFlow() {
        return IntegrationFlow.from("sensorErrorChannel")
                .handle(SensorErrorFlowConfig::logFailure)
                .get();
    }

    private static void logFailure(Message<?> message) {
        Object payload = message.getPayload();

        if (payload instanceof ProcessingFailure(Long brokerId, Throwable cause)) {
            log.error("센서 메시지 처리 실패: brokerId({})", brokerId, cause);
        } else {
            log.error("센서 메시지 처리 실패 (brokerId 불명): payload={}", payload);
        }
    }

    public record ProcessingFailure(Long brokerId, Throwable cause) {
    }
}
