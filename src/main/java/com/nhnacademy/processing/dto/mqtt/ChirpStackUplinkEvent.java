package com.nhnacademy.processing.dto.mqtt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChirpStackUplinkEvent(
        Instant time,
        DeviceInfo deviceInfo,
        Map<String, Object> object,
        List<RxInfo> rxInfo
) {
    public record DeviceInfo(
            String applicationId,
            String applicationName,
            String deviceProfileId,
            String deviceName,
            String devEui,
            Map<String, String> tags
    ){}
    public record RxInfo(
            Integer rssi,
            Double snr
    ) {}
}
