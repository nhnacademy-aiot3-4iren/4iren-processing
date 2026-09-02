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

    @Operation(summary = "건물 내 미배정 센서 목록 조회", description = "지정한 건물에서 아직 어떤 룸에도 배정되지 않은 센서 목록을 반환합니다. " +
            "룸에 '센서 추가' UI에서 이 API를 사용하면 이미 다른 룸에 배정된 센서가 후보 목록에 섞여 나오는 문제를 피할 수 있습니다.")
    @GetMapping("/sensors/buildings/{buildingId}/unassigned")
    public ResponseEntity<List<SensorSummaryResponse>> getUnassignedSensorsByBuilding(@PathVariable Long buildingId) {
        return ResponseEntity.ok(sensorDeviceService.getUnassignedSensorsByBuildingId(buildingId));
    }

    @Operation(summary = "센서-Room 매칭 (ADMIN)", description = "등록된 sensorDevice에 roomId를 할당합니다. " +
            "roomId를 null로 보내면 해당 센서의 룸 배정을 해제합니다(센서를 룸에서 삭제할 때 사용). (ADMIN 권한 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "매칭/해제 성공"),
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

    @Operation(summary = "룸 삭제 시 소속 센서 일괄 해제 (ADMIN)", description = "roomId에 배정되어 있던 모든 센서의 roomId를 null로 초기화합니다. " +
            "룸 삭제 이벤트가 발생했을 때 호출하는 내부 API입니다. (ADMIN 권한 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "해제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 헤더 누락"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @DeleteMapping("/rooms/{roomId}/sensors")
    public ResponseEntity<Void> unassignRoom(@LoginUser AuthUser authUser, @PathVariable Integer roomId) {
        sensorDeviceRegistry.unassignRoomAndEvictCache(roomId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "특정 방의 센서 목록 조회", description = "건물(buildingId)과 방(roomId)에 속한 센서 요약 정보를 조회합니다.")
    @GetMapping("/sensors/buildings/{buildingId}/rooms/{roomId}")
    public ResponseEntity<List<SensorSummaryResponse>> getSensorsByRoom(@PathVariable Long buildingId,
                                                                        @PathVariable Integer roomId) {
        return ResponseEntity.ok(sensorDeviceService.getSensorsByBuildingIdAndRoomId(buildingId, roomId));
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
