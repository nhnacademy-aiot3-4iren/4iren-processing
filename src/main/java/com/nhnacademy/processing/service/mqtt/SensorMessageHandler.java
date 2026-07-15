package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.dto.SensorRawMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 데이터 정제 (InfluxDB/RabbitMQ/Kibana)
 * 추후 구현 예정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorMessageHandler {

    public void handle(SensorRawMessage message) {

    }
}
