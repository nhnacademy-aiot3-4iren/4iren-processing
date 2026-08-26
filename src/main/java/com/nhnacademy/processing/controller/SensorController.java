package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.auth.AuthUser;
import com.nhnacademy.processing.auth.LoginUser;
import com.nhnacademy.processing.auth.RequireAdmin;
import com.nhnacademy.processing.dto.sensor.*;
import com.nhnacademy.processing.service.sensor.SensorDeviceRegistry;
import com.nhnacademy.processing.service.sensor.SensorDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Sensor API", description = "센서 조회 및 관리 API")
@RestController
@RequestMapping("/api/processing")
@RequiredArgsConstructor
public class SensorController {

    private final SensorDeviceService sensorDeviceService;
    private final SensorDeviceRegistry sensorDeviceRegistry;

    @GetMapping("/sensors")
    public ResponseEntity<List<SensorInfoResponse>> getSensorList(@RequestParam int roomId) {
        return ResponseEntity.ok(sensorDeviceService.getSensorTopologyByRoomId(roomId));
    }

    @Operation(summary = "건물별 센서 목록 조회", description = "지정한 MQTT 브로커에 연결된 센서 목록(devEui, deviceName, point)을 반환합니다.")
    @GetMapping("/sensors/buildings/{buildingId}")
    public ResponseEntity<List<SensorSummaryResponse>> getSensorsByBuilding(@PathVariable Long buildingId) {
        return ResponseEntity.ok(sensorDeviceService.getSensorsByBuildingId(buildingId));
    }

    @Operation(summary = "센서-Room 매칭 (ADMIN)", description = "등록된 sensorDevice에 roomId를 할당합니다. (ADMIN 권한 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "매칭 성공"),
            @ApiResponse(responseCode = "400", description = "요청 DTO 유효성 검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 헤더 누락"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @PatchMapping("/sensors/rooms")
    public ResponseEntity<Void> assignRooms(@LoginUser AuthUser authUser,
                                            @Valid @RequestBody List<SensorRoomAssignmentRequest> requests) {
        sensorDeviceRegistry.assignRoomsAndEvictCache(requests);
        return ResponseEntity.noContent().build();
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
