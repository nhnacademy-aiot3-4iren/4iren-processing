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

        if (payload instanceof ProcessingFailure(Long brokerId, Integer roomId, String devEui, Throwable cause)) {
            log.error("센서 메시지 처리 실패: brokerId({}), roomId({}), devEui({})", brokerId, roomId, devEui, cause);
        } else {
            log.error("센서 메시지 처리 실패 (상세 정보 불명): payload={}", payload);
        }
    }

    /**
     * roomId/devEui를 함께 담아서, 실패 지점(SensorMqSubFlowConfig 등)에서는 별도로 로그를 남기지 않고
     * 이 에러 플로우 한 곳에서만 전체 컨텍스트를 로깅한다. (기존에는 실패 지점 + 에러 플로우 양쪽에서
     * 같은 예외가 2줄씩 중복 로깅되어 있었음)
     */
    public record ProcessingFailure(Long brokerId, Integer roomId, String devEui, Throwable cause) {
    }
}
