package com.nhnacademy.processing.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;

/**
 * sensorErrorChannel 구독 - 중앙 집중 에러 로깅
 *
 * MqttBrokerRegistry가 catch한 예외를 여기로 명시적으로 보냄
 * 어디서 실패가 났는지 한곳에서 확인 가능
 */
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

    /** MqttBrokerRegistry가 catch한 예외를 sensorErrorChannel로 보낼 때 담는 페이로드. */
    public record ProcessingFailure(Long brokerId, Throwable cause) {
    }
}
