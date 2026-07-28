package com.nhnacademy.processing.dto.parse;

import com.nhnacademy.processing.domain.SensorDevice;

public record DeviceIdentity(
        String applicationId,
        String applicationName,
        String deviceProfileId,
        String deviceName,
        String devEui,
        String location,
        Integer roomId
) {
    public static DeviceIdentity from(SensorDevice device) {
        return new DeviceIdentity(
                device.getApplicationId(),
                device.getApplicationName(),
                device.getDeviceProfileId(),
                device.getDeviceName(),
                device.getDevEui(),
                null,
                device.getRoomId()
        );
    }
}
