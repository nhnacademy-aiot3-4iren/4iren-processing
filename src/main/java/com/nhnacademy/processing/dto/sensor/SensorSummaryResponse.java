package com.nhnacademy.processing.dto.sensor;

import com.nhnacademy.processing.domain.SensorDevice;

public record SensorSummaryResponse(
        String devEui,
        Long buildingId,
        String deviceName,
        String location,
        String point
) {
    public static SensorSummaryResponse from(SensorDevice device) {
        return new SensorSummaryResponse(
                device.getDevEui(),
                device.getMqttBrokerInfo().getBuildingId(),
                device.getDeviceName(),
                device.getLocation(),
                device.getPoint()
        );
    }
}
