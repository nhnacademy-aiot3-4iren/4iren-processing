package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.auth.AuthUser;
import com.nhnacademy.processing.auth.LoginUser;
import com.nhnacademy.processing.auth.RequireAdmin;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.service.mqtt.MqttBrokerRegistry;
import com.nhnacademy.processing.service.mqtt.MqttBrokerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "MQTT Broker API", description = "MQTT 브로커 등록 및 관리 (ADMIN 전용)")
@RestController
@RequestMapping("/api/processing/mqtt")
@RequiredArgsConstructor
public class MqttBrokerController {

    private final MqttBrokerService mqttBrokerService;
    private final MqttBrokerRegistry mqttBrokerRegistry;

    @Operation(summary = "건물별 MQTT 브로커 조회", description = "빌딩에 등록된 MQTT 브로커 정보를 조회합니다.")
    @GetMapping("/building/{buildingId}")
    public ResponseEntity<MqttBrokerInfoDto> getBrokerByBuilding(@PathVariable Long buildingId) {
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
        mqttBrokerRegistry.unregisterBroker(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "빌딩 소속 MQTT 브로커 일괄 삭제", description = "Core에서 빌딩(및 하위 룸)을 삭제할 때 호출하는 API. 해당 buildingId에 등록된 브로커를 하위 디바이스/측정 데이터까지 cascade로 삭제하고, 성공한 브로커에 한해 런타임 구독을 해제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 완료(대상이 없었어도 204)"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @DeleteMapping("/building/{buildingId}")
    public ResponseEntity<Void> deleteBrokersByBuilding(@LoginUser AuthUser authUser,
                                                        @PathVariable Long buildingId) {
        List<Long> deletedBrokerIds = mqttBrokerService.deleteByBuildingId(buildingId);
        deletedBrokerIds.forEach(mqttBrokerRegistry::unregisterBroker);
        return ResponseEntity.noContent().build();
    }
}
