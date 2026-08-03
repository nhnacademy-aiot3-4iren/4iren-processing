package com.nhnacademy.processing.client;

import com.nhnacademy.processing.dto.api.SensorContext;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name="4iren-core", path="/api/core")
public interface CoreClient {

    @GetMapping("/internal/sensors/{devEui}/telemetry-context")
    SensorContext fetch(@PathVariable("devEui") String devEui);
}
