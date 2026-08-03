package com.nhnacademy.processing.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // DTO에 정의되지 않은 필드가 JSON에 포함되어 있으면 무시
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);   // JSON 직렬화 시 null 필드 제외
        return mapper;
    }

//    @Bean
//    public RestClient sensorContextRestClient(@Value("${sensor-context.base-url}") String baseUrl) {
//        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
//                .withConnectTimeout(Duration.ofSeconds(2))
//                .withReadTimeout(Duration.ofSeconds(2));
//
//        ClientHttpRequestFactory requestFactory  = ClientHttpRequestFactoryBuilder.detect().build(settings);
//
//        return RestClient.builder()
//                .baseUrl(baseUrl)
//                .requestFactory(requestFactory)
//                .build();
//    }
}