package com.nhnacademy.processing.dto.sensor;

import java.time.Instant;
import java.util.List;

public record ParsedSensorMessage(
        DeviceIdentity device,
        List<SensorData> sensorDataList,
        Instant measuredAt
) {
}
