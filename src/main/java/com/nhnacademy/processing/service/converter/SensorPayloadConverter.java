package com.nhnacademy.processing.service.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.dto.mqtt.ChirpStackUplinkEvent;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.exception.SensorPayloadParseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorPayloadConverter {

    private final ObjectMapper objectMapper;

    private static final Set<String> ENV_MEASUREMENTS = Set.of("co2", "humidity", "illumination", "pressure", "temperature", "door", "tvoc");
    private static final String DEVICE_HEALTH_FIELD = "battery";

    public ParsedSensorMessage convert(String rawPayload) {
        ChirpStackUplinkEvent event;
        try {
            event = objectMapper.readValue(rawPayload, ChirpStackUplinkEvent.class);
        } catch (JsonProcessingException e) {
            throw new SensorPayloadParseException("ChirpStack payload 파싱 실패", e);
        }

        if(event.deviceInfo() == null || event.deviceInfo().devEui() == null) {
            throw new SensorPayloadParseException("deviceInfo 또는 devEui 누락");
        }


        var tags = event.deviceInfo().tags();
        String point = (tags == null) ? null : tags.getOrDefault("point", null);
        String location = (tags == null) ? null : tags.getOrDefault("location", null);

        DeviceIdentity device = new DeviceIdentity(event.deviceInfo().applicationId(),
                                                    event.deviceInfo().applicationName(),
                                                    event.deviceInfo().deviceProfileId(),
                                                    event.deviceInfo().deviceName(),
                                                    event.deviceInfo().devEui(),
                                                    null,
                                                    location,
                                                    point);

        List<SensorData> sensorDataList = new ArrayList<>();

        // object가 있을 때만 환경 데이터 및 디바이스 상태 파싱
        if (event.object() != null && !event.object().isEmpty()) {
            event.object().forEach((measurement, rawValue) -> {
                if(ENV_MEASUREMENTS.contains(measurement)) {
                    sensorDataList.add(new SensorData(MeasurementCategory.ENVIRONMENT, measurement, toDouble(rawValue)));
                } else if (DEVICE_HEALTH_FIELD.equals(measurement)) {
                    sensorDataList.add(new SensorData(MeasurementCategory.DEVICE_HEALTH, measurement, toDouble(rawValue)));
                }
            });
        }

        // 통신 품질(RSSI, SNR) 데이터는 object 여부와 관계없이 항상 수집
        if (event.rxInfo() != null && !event.rxInfo().isEmpty()) {
            event.rxInfo().stream()
                    .filter(rx -> rx.snr() != null)
                    .findFirst()
                    .ifPresentOrElse(
                            rx -> {
                                sensorDataList.add(new SensorData(MeasurementCategory.NETWORK_QUALITY, "rssi", toDouble(rx.rssi())));
                                sensorDataList.add(new SensorData(MeasurementCategory.NETWORK_QUALITY, "snr", toDouble(rx.snr())));
                            },
                            () -> log.debug("모든 rxInfo에 snr 값이 없어 네트워크 품질 데이터를 생략함: devEui={}",
                                    event.deviceInfo().devEui())
                    );
        }

        return new ParsedSensorMessage(device, sensorDataList, event.time());
    }

    private double toDouble(Object value) {
        return switch (value) {
            case null -> 0.0;
            case Number num -> num.doubleValue();
            case Boolean bool -> bool ? 1.0 : 0.0;
            case String str -> strToDouble(str);
            default -> 0.0;
        };
    }

    private double strToDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}