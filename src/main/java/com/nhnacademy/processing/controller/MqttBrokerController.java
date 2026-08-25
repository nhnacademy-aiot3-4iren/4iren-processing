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

@Tag(name = "MQTT Broker API", description = "MQTT 브로커 등록 및 관리 (ADMIN 전용)")
@RestController
@RequestMapping("/api/processing/mqtt")
@RequiredArgsConstructor
public class MqttBrokerController {

    private final MqttBrokerService mqttBrokerService;
    private final MqttBrokerRegistry mqttBrokerRegistry;

    @Operation(summary = "MQTT 브로커 등록", description = "새로운 MQTT 브로커를 DB에 저장하고 메시지 인바운드 플로우를 활성화합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "브로커 등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 DTO 유효성 검증 실패 또는 등록 오류"),
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

    @Operation(summary = "MQTT 브로커 삭제", description = "브로커 연동 플로우를 해제하고 브로커 정보를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "브로커 삭제 완료"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @RequireAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBroker(@LoginUser AuthUser authUser,
                                             @PathVariable Long id) {
        mqttBrokerRegistry.unregisterBroker(id);
        mqttBrokerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
