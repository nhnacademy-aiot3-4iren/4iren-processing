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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            throw new SensorPayloadParseException("ChirpStack payload 역직렬화 실패", e);
        }

        if(event.deviceInfo() == null || event.deviceInfo().devEui() == null) {
            throw new SensorPayloadParseException("deviceInfo 또는 devEui 누락");
        }
        if(event.object() == null || event.object().isEmpty()) {
            throw new SensorPayloadParseException("측정값 없음(" + event.deviceInfo().devEui());
        }


        String location = (event.deviceInfo().tags() == null) ? "unknown" : event.deviceInfo().tags().getOrDefault("location", "unknown");
        DeviceIdentity device = new DeviceIdentity(event.deviceInfo().applicationId(),
                                                    event.deviceInfo().applicationName(),
                                                    event.deviceInfo().deviceProfileId(),
                                                    event.deviceInfo().deviceName(),
                                                    event.deviceInfo().devEui(),
                                                    location);
        List<SensorData> sensorDataList = new ArrayList<>();
        event.object().forEach((measurement, rawValue) -> {
            if(ENV_MEASUREMENTS.contains(measurement)) {
                sensorDataList.add(new SensorData(MeasurementCategory.ENVIRONMENT, measurement, toDouble(rawValue)));
            } else if (DEVICE_HEALTH_FIELD.equals(measurement)) {
                sensorDataList.add(new SensorData(MeasurementCategory.DEVICE_HEALTH, measurement, toDouble(rawValue)));
            }
        });

        if(event.rxInfo() != null && !event.rxInfo().isEmpty()) {
            sensorDataList.add(new SensorData(MeasurementCategory.NETWORK_QUALITY, "rssi", toDouble(event.rxInfo().getFirst().rssi())));
            sensorDataList.add(new SensorData(MeasurementCategory.NETWORK_QUALITY, "snr", toDouble(event.rxInfo().getFirst().snr())));
        }

        return new ParsedSensorMessage(device, sensorDataList, event.time());
    }

    private double toDouble(Object value) {
        return switch (value) {
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
