package com.nhnacademy.processing.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageChannel;

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

    @Bean
    public MessageChannel sensorErrorChannel() {
        return new DirectChannel();
    }
}
