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
public class RegisConfig {

    @Bean
    public RedisTemplate<String, EnvironmentContext> environmentContextRedisTemplate(RedisConnectionFactory connectionFactory,
                                                                                     ObjectMapper objectMapper) {
        RedisTemplate<String, EnvironmentContext> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<EnvironmentContext> valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, EnvironmentContext.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();

        return template;
    }
}
