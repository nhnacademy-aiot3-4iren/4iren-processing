package com.nhnacademy.processing.dto.sensor;

import java.util.Map;

public record DeviceIdentity(
        String applicationId,
        String applicationName,
        String deviceProfileId,
        String deviceName,
        String devEui,
        Map<String, String> tags

) {
}
