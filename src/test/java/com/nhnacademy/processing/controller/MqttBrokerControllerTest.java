package com.nhnacademy.processing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.service.mqtt.MqttBrokerRegistry;
import com.nhnacademy.processing.service.mqtt.MqttBrokerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MqttBrokerController.class)
class MqttBrokerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MqttBrokerService mqttBrokerService;
    @MockitoBean private MqttBrokerRegistry mqttBrokerRegistry;

    @Test
    @DisplayName("MQTT 브로커 정보 등록")
    void registerBroker_Success() throws Exception {
        MqttBrokerCreateRequest request = new MqttBrokerCreateRequest(
                "broker",
                "tcp://localhost:1883",
                "testUser",
                "testPass",
                "application/+/device/+/event/up"
        );

        MqttBrokerInfoDto responseDto = new MqttBrokerInfoDto(
                1L,
                request.serverName(),
                request.brokerUrl(),
                request.username(),
                request.password(),
                request.topic()
        );

        when(mqttBrokerService.register(any(MqttBrokerCreateRequest.class))).thenReturn(responseDto);
        doNothing().when(mqttBrokerRegistry).registerBroker(any(MqttBrokerInfoDto.class));

        mockMvc.perform(post("/api/processing/mqtt")
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
    @DisplayName("MQTT 브로커 정보 삭제")
    void deleteBroker_Success() throws Exception {
        Long brokerId = 1L;

        doNothing().when(mqttBrokerRegistry).unregisterBroker(brokerId);
        doNothing().when(mqttBrokerService).delete(brokerId);

        mockMvc.perform(delete("/api/processing/mqtt/{id}", brokerId))
                .andExpect(status().isNoContent());

        verify(mqttBrokerRegistry).unregisterBroker(brokerId);
        verify(mqttBrokerService).delete(brokerId);
    }
}