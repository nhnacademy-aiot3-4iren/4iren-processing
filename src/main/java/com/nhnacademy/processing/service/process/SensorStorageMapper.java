package com.nhnacademy.processing.service.process;

import com.nhnacademy.processing.dto.influx.SensorInfluxPointDto;
import com.nhnacademy.processing.dto.sensor.DeviceIdentity;
import com.nhnacademy.processing.dto.sensor.ParsedSensorMessage;
import com.nhnacademy.processing.dto.sensor.SensorData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SensorStorageMapper {

    public List<SensorInfluxPointDto> toInfluxPoints(ParsedSensorMessage message, String roomId) {
        DeviceIdentity device = message.device();

        return message.sensorDataList().stream()
                .map(data -> new SensorInfluxPointDto(
                        data.measurement(),
                        data.value(),
                        message.measuredAt(),
                        device.applicationId(),
                        device.devEui(),
                        device.deviceName(),
                        roomId
                ))
                .toList();
    }
}
