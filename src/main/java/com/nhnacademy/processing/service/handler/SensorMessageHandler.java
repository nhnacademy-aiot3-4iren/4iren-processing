package com.nhnacademy.processing.service.handler;

import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.service.converter.SensorPayloadConverter;
import com.nhnacademy.processing.service.es.SensorAnomalyLogService;
import com.nhnacademy.processing.service.influx.InfluxDbWriter;
import com.nhnacademy.processing.service.process.SensorContextResolver;
import com.nhnacademy.processing.service.rabbitmq.RabbitMqPublisher;
import com.nhnacademy.processing.service.sensor.SensorDeviceRegistry;
import com.nhnacademy.processing.service.validation.SensorValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorMessageHandler {

    private final SensorPayloadConverter payloadConverter;
    private final SensorContextResolver contextResolver;
    private final InfluxDbWriter influxDbWriter;
    private final RabbitMqPublisher rabbitMqPublisher;
    private final SensorValidator sensorValidator;
    private final SensorAnomalyLogService sensorAnomalyLogService;
    private final SensorDeviceRegistry sensorDeviceRegistry;

    public void handle(Long brokerId, Message<?> message) {
        // mqtt 페이로드 파싱
        ParsedSensorMessage parsedMessage;
        try {
            parsedMessage = payloadConverter.convert(message.getPayload().toString());
        } catch (Exception e) {
            log.error("메시지 파싱 실패", e);
            return;
        }

        // devEui로 roomId 획득
        String devEui = parsedMessage.device().devEui();
        SensorContext roomContext = contextResolver.resolve(devEui)
                .orElseGet(() -> new SensorContext(devEui, -1, -1));
        int roomId = roomContext.roomId();

        // 신규 센서 자동 등록
        try{
            sensorDeviceRegistry.ensureRegistered(parsedMessage, brokerId, roomId);
        } catch (Exception e) {
            log.error("센서 자동 등록 중 예외 발생: devEui({})", devEui, e);
        }

        // 센서 측정값 개별 순회 및 적재/라우팅
        parsedMessage.sensorDataList().forEach(data -> {
            try {
                process(data, parsedMessage, roomId);
            } catch (Exception e) {
                log.error("측정값 처리 실패: measurement({}), devEui({})", data.measurement(), devEui, e);
            }
        });
    }


    private void process(SensorData data, ParsedSensorMessage message, int roomId) {
        switch (data.category()) {
            case NETWORK_QUALITY -> {
                influxDbWriter.writeAsync(data, message, roomId);
            }
            case DEVICE_HEALTH -> {
                influxDbWriter.writeAsync(data, message, roomId);
                publishIfRoomKnown(data, message, roomId);
            }
            case ENVIRONMENT -> {
                processEnvironment(data, message, roomId);
            }
        }
    }

    private void processEnvironment(SensorData data, ParsedSensorMessage message, int roomId) {
        ValidationStatus status = sensorValidator.validate(data);

        if (ValidationStatus.VALID.equals(status)) {
            influxDbWriter.writeAsync(data, message, roomId);
            publishIfRoomKnown(data, message, roomId);
        } else {
            sensorAnomalyLogService.log(data, message.device().devEui(), roomId, status, message.measuredAt());
        }
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