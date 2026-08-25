package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.dto.sensor.MetricTypeResponse;
import com.nhnacademy.processing.dto.sensor.SensorBatchRequest;
import com.nhnacademy.processing.dto.sensor.SensorInfoResponse;
import com.nhnacademy.processing.service.sensor.SensorDeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Sensor API", description = "센서 및 메트릭 조회 (인증 불필요)")
@RestController
@RequestMapping("/api/processing")
@RequiredArgsConstructor
public class SensorController {

    private final SensorDeviceService sensorDeviceService;

    @GetMapping("/sensors")
    public ResponseEntity<List<SensorInfoResponse>> getSensorList(@RequestParam int roomId) {
        return ResponseEntity.ok(sensorDeviceService.getSensorTopologyByRoomId(roomId));
    }

    @GetMapping("/metric_type")
    public ResponseEntity<Map<String, List<MetricTypeResponse>>> getMetricTypeList(@RequestParam String devEui) {
        return ResponseEntity.ok(Map.of(devEui, sensorDeviceService.getMetricTypesByDevEui(devEui)));
    }

    @GetMapping("/internal/metric-catalog")
    public ResponseEntity<List<MetricTypeResponse>> getMetricCatalog() {
        return ResponseEntity.ok(sensorDeviceService.getAllMetricCatalog());
    }

    @PostMapping("/internal/sensors/batch")
    public ResponseEntity<Map<String, List<MetricTypeResponse>>> getMetricTypeCatalog(@RequestBody SensorBatchRequest request) {
        return ResponseEntity.ok(sensorDeviceService.getMetricTypesByDevEuis(request.devEuis()));
    }
}
