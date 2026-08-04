package com.nhnacademy.processing.config.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageChannel;

/**
 * 센서 처리 파이프라인에서 사용하는 MessageChannel Bean 모음.
 * - sensorInputChannel : MqttBrokerRegistry가 파싱 전 원본 메시지를 밀어넣는 진입점
 * - sensorLoggingChannel : WireTap으로 복제된 메시지를 로깅만 하는 관찰용 채널
 * - sensorPubSubChannel : 전처리(파싱/roomId조회/기기등록) 완료 후 DB 저장과 MQ 발행으로
 *                         갈라지는 fan-out 지점.
 */
@Configuration
public class SensorIntegrationChannelConfig {

    @Bean
    public MessageChannel sensorInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel sensorLoggingChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel sensorPubSubChannel() {
        return new PublishSubscribeChannel();
    }
}
