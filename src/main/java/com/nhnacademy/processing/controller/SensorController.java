package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.dto.sensor.MetricTypeResponse;
import com.nhnacademy.processing.dto.sensor.SensorBatchRequest;
import com.nhnacademy.processing.dto.sensor.SensorInfoResponse;
import com.nhnacademy.processing.dto.sensor.SensorSummaryResponse;
import com.nhnacademy.processing.service.sensor.SensorDeviceService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "브로커별 센서 목록 조회", description = "지정한 MQTT 브로커에 연결된 센서 목록(devEui, deviceName, point)을 반환합니다.")
    @GetMapping("/sensors/brokers/{brokerId}")
    public ResponseEntity<List<SensorSummaryResponse>> getSensorsByBroker(@PathVariable Long brokerId) {
        return ResponseEntity.ok(sensorDeviceService.getSensorsByBrokerId(brokerId));
    }

    @Operation(summary = "방별 센서 목록 조회", description = "지정한 방(roomId)에 배정된 센서 목록(devEui, deviceName, point)을 반환합니다.")
    @GetMapping("/sensors/rooms/{roomId}")
    public ResponseEntity<List<SensorSummaryResponse>> getSensorsByRoom(@PathVariable Integer roomId) {
        return ResponseEntity.ok(sensorDeviceService.getSensorsByRoomId(roomId));
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
