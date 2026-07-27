package com.nhnacademy.processing.dto.parse;

public record DeviceIdentity(
        String applicationId,
        String applicationName,
        String deviceProfileId,
        String deviceName,
        String devEui,
        String location
) {
}
