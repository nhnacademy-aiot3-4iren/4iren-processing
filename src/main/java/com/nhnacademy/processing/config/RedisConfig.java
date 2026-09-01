package com.nhnacademy.processing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.dto.context.EnvironmentContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, EnvironmentContext.MetricInfo> environmentContextRedisTemplate(RedisConnectionFactory connectionFactory,
                                                                                                ObjectMapper objectMapper) {

        RedisTemplate<String, EnvironmentContext.MetricInfo> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<EnvironmentContext.MetricInfo> metricInfoSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, EnvironmentContext.MetricInfo.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key: "env:context:{roomId}"
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(metricInfoSerializer);

        // Hash Field: "temperature", "co2" / Hash Value: MetricInfo JSON
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(metricInfoSerializer);

        template.afterPropertiesSet();
        return template;
    }
}