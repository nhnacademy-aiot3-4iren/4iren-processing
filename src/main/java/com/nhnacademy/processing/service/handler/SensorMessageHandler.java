package com.nhnacademy.processing.service.handler;

import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.service.rabbitmq.RabbitMqPublisher;
import com.nhnacademy.processing.service.validation.SensorValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 개별 SensorData 순회 -> 카테고리/유효성에 따른 분류 -> DB 저장 / RabbitMQ 발행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorMessageHandler {

    private final RabbitMqPublisher rabbitMqPublisher;
    private final SensorValidator sensorValidator;

    public void publishAll(ParsedSensorMessage message, int roomId) {
        String devEui = message.device().devEui();
        message.sensorDataList().forEach(data -> {
            try {
                switch (data.category()) {
                    case DEVICE_HEALTH -> {
                        publishIfRoomKnown(data, message, roomId);
                    }
                    case ENVIRONMENT -> {
                        if(ValidationStatus.VALID.equals(sensorValidator.validate(data))) {
                            publishIfRoomKnown(data, message, roomId);
                        }
                    }
                    case NETWORK_QUALITY -> {

                    }
                }
            } catch (Exception e) {
                log.error("RabbitMQ 발행 처리 실패: measurement({}), devEui({})", data.measurement(), devEui, e);
            }
        });
    }

    private void publishIfRoomKnown(SensorData data, ParsedSensorMessage parsed, int roomId) {
        if (roomId < 0) {
            log.warn("roomId 미확인으로 RabbitMQ 발행 제외: measurement={}, devEui={}",
                    data.measurement(), parsed.device().devEui());
            return;
        }

        DeviceIdentity newDevice = new DeviceIdentity(parsed.device().applicationId(),
                parsed.device().applicationName(),
                parsed.device().deviceProfileId(),
                parsed.device().deviceName(),
                parsed.device().devEui(),
                null,
                roomId);
        ParsedSensorMessage message = new ParsedSensorMessage(newDevice, parsed.sensorDataList(), parsed.measuredAt());
        rabbitMqPublisher.publish(message);
    }
}