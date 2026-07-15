package com.nhnacademy.processing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class MqttExecutorConfig {

    @Bean
    public ExecutorService mqttProcessingExecutor() {
        return new ThreadPoolExecutor(
                16, 32, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
