package com.nhnacademy.processing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.auth.AuthHeaders;
import com.nhnacademy.processing.auth.AuthUserArgumentResolver;
import com.nhnacademy.processing.auth.AuthenticationInterceptor;
import com.nhnacademy.processing.config.WebConfig;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerUpdateRequest;
import com.nhnacademy.processing.service.mqtt.MqttBrokerRegistry;
import com.nhnacademy.processing.service.mqtt.MqttBrokerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttBrokerController.class)
@Import({WebConfig.class, AuthenticationInterceptor.class, AuthUserArgumentResolver.class, GlobalExceptionHandler.class})
class MqttBrokerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private MqttBrokerService mqttBrokerService;
    @MockitoBean private MqttBrokerRegistry mqttBrokerRegistry;

    private MqttBrokerInfoDto mockDto() {
        return new MqttBrokerInfoDto(1L, 101L, "broker", "tcp://localhost:1883", "user", "pass", "topic");
    }

    // --- 기존 등록 및 삭제 테스트 생략 (문제 원문에 있는 테스트 코드 유지) ---
    // registerBroker_Success, registerBroker_forbidden 등 기존 테스트는 이 위치에 포함됩니다.

    @Test
    @DisplayName("빌딩 ID로 MQTT 브로커 조회 - 성공")
    void getBrokerByBuilding_Success() throws Exception {
        when(mqttBrokerService.getBrokerByBuildingId(101L)).thenReturn(Optional.of(mockDto()));

        mockMvc.perform(get("/api/processing/mqtt/building/{buildingId}", 101L)
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.serverName").value("broker"));
    }

    @Test
    @DisplayName("빌딩 ID로 MQTT 브로커 조회 - 없음 (null 반환)")
    void getBrokerByBuilding_NotFound() throws Exception {
        when(mqttBrokerService.getBrokerByBuildingId(101L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/processing/mqtt/building/{buildingId}", 101L)
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    @DisplayName("MQTT 브로커 업데이트 - 성공")
    void updateBroker_Success() throws Exception {
        MqttBrokerUpdateRequest request = new MqttBrokerUpdateRequest("newBroker", "tcp://new:1883", "usr", "pwd", "new/topic");
        MqttBrokerInfoDto responseDto = new MqttBrokerInfoDto(1L, 101L, "newBroker", "tcp://new:1883", "usr", "pwd", "new/topic");

        when(mqttBrokerService.getBrokerByBuildingId(101L)).thenReturn(Optional.of(mockDto()));
        when(mqttBrokerService.updateByBuilding(eq(101L), any())).thenReturn(responseDto);

        mockMvc.perform(put("/api/processing/mqtt/building/{buildingId}", 101L)
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverName").value("newBroker"));

        verify(mqttBrokerRegistry).unregisterBroker(1L);
        verify(mqttBrokerRegistry).registerBroker(any(MqttBrokerInfoDto.class));
    }

    @Test
    @DisplayName("MQTT 브로커 업데이트 중 Registry 실패 시 롤백 수행")
    void updateBroker_RegistryFailure_Rollback() throws Exception {
        MqttBrokerUpdateRequest request = new MqttBrokerUpdateRequest("newBroker", "tcp://new:1883", "usr", "pwd", "new/topic");
        MqttBrokerInfoDto previous = mockDto();
        MqttBrokerInfoDto updated = new MqttBrokerInfoDto(1L, 101L, "newBroker", "tcp://new:1883", "usr", "pwd", "new/topic");

        when(mqttBrokerService.getBrokerByBuildingId(101L)).thenReturn(Optional.of(previous));
        when(mqttBrokerService.updateByBuilding(eq(101L), any())).thenReturn(updated);

        doThrow(new RuntimeException("Registry Error")).when(mqttBrokerRegistry).registerBroker(updated);

        mockMvc.perform(put("/api/processing/mqtt/building/{buildingId}", 101L)
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(mqttBrokerService, times(2)).updateByBuilding(eq(101L), any(MqttBrokerUpdateRequest.class));
        verify(mqttBrokerRegistry).registerBroker(previous); // 이전 상태로 재등록 확인
    }

    @Test
    @DisplayName("빌딩 내 모든 브로커 삭제 - 성공")
    void deleteBrokersByBuilding_Success() throws Exception {
        when(mqttBrokerService.deleteByBuildingId(101L)).thenReturn(Optional.of(1L));

        mockMvc.perform(delete("/api/processing/mqtt/building/{buildingId}", 101L)
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNoContent());

        verify(mqttBrokerService).deleteByBuildingId(101L);
        verify(mqttBrokerRegistry).unregisterBroker(1L);
    }

    @Test
    @DisplayName("단일 브로커 삭제 중 Registry 예외 발생 시 에러 로깅 후 204 반환")
    void deleteBroker_RegistryException_Returns204() throws Exception {
        Long brokerId = 1L;
        doThrow(new RuntimeException("Unregister Fail")).when(mqttBrokerRegistry).unregisterBroker(brokerId);

        mockMvc.perform(delete("/api/processing/mqtt/{id}", brokerId)
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNoContent());

        verify(mqttBrokerService).delete(brokerId);
    }
}