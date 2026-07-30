package com.nhnacademy.processing.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChirpStackUplinkEvent(
        Instant time,
        DeviceInfo deviceInfo,
        Map<String, Object> object,
        List<RxInfo> rxInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceInfo(
            String applicationId,
            String applicationName,
            String deviceProfileId,
            String deviceName,
            String devEui,
            Map<String, String> tags
    ){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RxInfo(
            Integer rssi,
            Double snr
    ) {}
}
