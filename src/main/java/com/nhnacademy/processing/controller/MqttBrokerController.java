package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.auth.AuthUser;
import com.nhnacademy.processing.auth.LoginUser;
import com.nhnacademy.processing.auth.RequireAdmin;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerUpdateRequest;
import com.nhnacademy.processing.exception.MqttBrokerNotFoundException;
import com.nhnacademy.processing.service.mqtt.MqttBrokerRegistry;
import com.nhnacademy.processing.service.mqtt.MqttBrokerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "MQTT Broker API", description = "MQTT 브로커 등록 및 관리 (ADMIN 전용)")
@RestController
@RequestMapping("/api/processing/mqtt")
@RequiredArgsConstructor
public class MqttBrokerController {

    private final MqttBrokerService mqttBrokerService;
    private final MqttBrokerRegistry mqttBrokerRegistry;

    @RequireAdmin
    @Operation(summary = "건물별 MQTT 브로커 조회", description = "빌딩에 등록된 MQTT 브로커 정보를 조회합니다.")
    @GetMapping("/building/{buildingId}")
    public ResponseEntity<MqttBrokerInfoDto> getBrokerByBuilding(@LoginUser AuthUser authUser,
                                                                 @PathVariable Long buildingId) {
        return mqttBrokerService.getBrokerByBuildingId(buildingId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(null));
    }

    @Operation(summary = "MQTT 브로커 등록", description = "새로운 MQTT 브로커를 DB에 저장하고 메시지 인바운드 플로우를 활성화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "브로커 등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 DTO 유효성 검증 실패, 또는 URL/username/password가 잘못되어 MQTT 구독(연결)에 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @PostMapping
    public ResponseEntity<MqttBrokerInfoDto> registerBroker(@LoginUser AuthUser authUser,
                                                            @Valid @RequestBody MqttBrokerCreateRequest request) {
        MqttBrokerInfoDto broker = mqttBrokerService.register(request);
        try {
            mqttBrokerRegistry.registerBroker(broker);
        } catch (Exception e) {
            mqttBrokerService.delete(broker.id());
            throw e;
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(broker);
    }

    @Operation(summary = "MQTT 브로커 정보 수정", description = "건물 단위 MQTT 브로커 정보를 수정합니다.")
    @RequireAdmin
    @PutMapping("/building/{buildingId}")
    public ResponseEntity<MqttBrokerInfoDto> updateBroker(@LoginUser AuthUser authUser,
                                                          @PathVariable Long buildingId,
                                                          @Valid @RequestBody MqttBrokerUpdateRequest request) {
        MqttBrokerInfoDto previous = mqttBrokerService.getBrokerByBuildingId(buildingId)
                .orElseThrow(() -> new MqttBrokerNotFoundException(buildingId));
        MqttBrokerInfoDto broker = mqttBrokerService.updateByBuilding(buildingId, request);
        try {
            mqttBrokerRegistry.unregisterBroker(broker.id());
            mqttBrokerRegistry.registerBroker(broker);
        } catch (Exception e) {
            log.error("브로커 갱신 실패 - buildingId({}), brokerId({}) 이전 설정으로 복구", buildingId, broker.id(), e);
            mqttBrokerService.updateByBuilding(buildingId, previous.toUpdateRequest());
            try {
                mqttBrokerRegistry.registerBroker(previous);
            } catch (Exception rollbackEx) {
                log.error("브로커 이전 설정 런타임 복구 실패: brokerId={}", previous.id(), rollbackEx);
            }
            throw e;
        }
        return ResponseEntity.ok(broker);
    }

    @Operation(summary = "MQTT 브로커 삭제", description = "브로커 정보(및 하위 디바이스/측정 데이터)를 DB에서 먼저 삭제하고, 성공한 경우에만 런타임 구독을 해제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "브로커 삭제 완료(대상이 없었어도 204)"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBroker(@LoginUser AuthUser authUser,
                                             @PathVariable Long id) {
        mqttBrokerService.delete(id);
        try {
            mqttBrokerRegistry.unregisterBroker(id);
        } catch (Exception e) {
            log.error("런타임 MQTT 브로커 구독 해제 실패 (DB는 삭제 완료): brokerId={}", id, e);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "건물 단위 MQTT 브로커 삭제", description = "Core 동기화용 API. 특정 buildingId에 바인딩된 브로커를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @DeleteMapping("/building/{buildingId}")
    public ResponseEntity<Void> deleteBrokersByBuilding(@LoginUser AuthUser authUser,
                                                        @PathVariable Long buildingId) {
        mqttBrokerService.deleteByBuildingId(buildingId).ifPresent(brokerId -> {
            try {
                mqttBrokerRegistry.unregisterBroker(brokerId);
            } catch (Exception e) {
                log.error("런타임 MQTT 브로커 구독 해제 실패: brokerId={}", brokerId, e);
            }
        });
        return ResponseEntity.noContent().build();
    }
}
