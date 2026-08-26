package com.nhnacademy.processing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.auth.AuthHeaders;
import com.nhnacademy.processing.auth.AuthUserArgumentResolver;
import com.nhnacademy.processing.auth.AuthenticationInterceptor;
import com.nhnacademy.processing.config.WebConfig;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttBrokerController.class)
@Import({WebConfig.class, AuthenticationInterceptor.class, AuthUserArgumentResolver.class, GlobalExceptionHandler.class})
class MqttBrokerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MqttBrokerService mqttBrokerService;
    @MockitoBean private MqttBrokerRegistry mqttBrokerRegistry;

    private MqttBrokerCreateRequest validRequest() {
        return new MqttBrokerCreateRequest(
                101L,
                "broker",
                "tcp://localhost:1883",
                "testUser",
                "testPass",
                "application/+/device/+/event/up"
        );
    }

    @Test
    @DisplayName("MQTT 브로커 등록")
    void registerBroker_Success() throws Exception {
        MqttBrokerCreateRequest request = validRequest();

        MqttBrokerInfoDto responseDto = new MqttBrokerInfoDto(
                1L,
                101L,
                request.serverName(),
                request.brokerUrl(),
                request.username(),
                request.password(),
                request.topic()
        );

        when(mqttBrokerService.register(any(MqttBrokerCreateRequest.class))).thenReturn(responseDto);
        doNothing().when(mqttBrokerRegistry).registerBroker(any(MqttBrokerInfoDto.class));

        mockMvc.perform(post("/api/processing/mqtt")
                        .header(AuthHeaders.USER_ID, 1)
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.serverName").value("broker"))
                .andExpect(jsonPath("$.brokerUrl").value("tcp://localhost:1883"))
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.topic").value("application/+/device/+/event/up"));

        verify(mqttBrokerService).register(any(MqttBrokerCreateRequest.class));
        verify(mqttBrokerRegistry).registerBroker(any(MqttBrokerInfoDto.class));
    }

    @Test
    @DisplayName("NOMAL 사용자가 broker 등록 시 403")
    void registerBroker_forbidden() throws Exception {
        mockMvc.perform(post("/api/processing/mqtt")
                        .header(AuthHeaders.USER_ID, 2)
                        .header(AuthHeaders.USER_ROLE, "NORMAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 헤더가 누락된 경우 401")
    void registerBroker_unauthorized() throws Exception {
        mockMvc.perform(post("/api/processing/mqtt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 헤더 일부 누락된 경우 400")
    void registerBroker_badRequest() throws Exception {
        mockMvc.perform(post("/api/processing/mqtt")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("요청 바디 검증 실패 400")
    void registerBroker_failValidation() throws Exception {
        MqttBrokerCreateRequest invalidRequest = new MqttBrokerCreateRequest(
                101L, "", "", null, null, ""
        );

        mockMvc.perform(post("/api/processing/mqtt")
                        .header(AuthHeaders.USER_ID, 3)
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Registry 등록 실패 시 Service에서 브로커 삭제")
    void registerBroker_registryFailure_rollback() throws Exception {
        MqttBrokerInfoDto mockDto = new MqttBrokerInfoDto(
                1L, 101L, "Test-Broker", "tcp://localhost:1883", "user", "pass", "sensor/#"
        );

        when(mqttBrokerService.register(any(MqttBrokerCreateRequest.class))).thenReturn(mockDto);
        doThrow(new IllegalStateException("MQTT Connect Error"))
                .when(mqttBrokerRegistry).registerBroker(any(MqttBrokerInfoDto.class));

        mockMvc.perform(post("/api/processing/mqtt")
                        .header(AuthHeaders.USER_ID, "1")
                        .header(AuthHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().is5xxServerError());

        verify(mqttBrokerService).delete(1L);
    }

    @Test
    @DisplayName("MQTT 브로커 정보 삭제")
    void deleteBroker_Success() throws Exception {
        Long brokerId = 1L;

        doNothing().when(mqttBrokerRegistry).unregisterBroker(brokerId);
        doNothing().when(mqttBrokerService).delete(brokerId);

        mockMvc.perform(delete("/api/processing/mqtt/{id}", brokerId)
                        .header(AuthHeaders.USER_ID, 4)
                        .header(AuthHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isNoContent());

        verify(mqttBrokerRegistry).unregisterBroker(brokerId);
        verify(mqttBrokerService).delete(brokerId);
    }

    @Test
    @DisplayName("NOMAL 사용자가 broker 삭제 시 403")
    void deleteBroker_forbidden() throws Exception {
        mockMvc.perform(delete("/api/processing/mqtt/{id}", 1L)
                        .header(AuthHeaders.USER_ID, 5)
                        .header(AuthHeaders.USER_ROLE, "NORMAL"))
                .andExpect(status().isForbidden());

        verify(mqttBrokerRegistry, never()).unregisterBroker(1L);
        verify(mqttBrokerService, never()).delete(1L);
    }
}