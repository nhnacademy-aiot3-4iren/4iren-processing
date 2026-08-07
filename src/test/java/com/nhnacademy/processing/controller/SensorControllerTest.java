package com.nhnacademy.processing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.dto.sensor.MetricTypeResponse;
import com.nhnacademy.processing.dto.sensor.SensorBatchRequest;
import com.nhnacademy.processing.dto.sensor.SensorInfoResponse;
import com.nhnacademy.processing.service.sensor.SensorDeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SensorController.class)
class SensorControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private SensorDeviceService sensorDeviceService;

    @Test
    @DisplayName("특정 roomId의 센서 목록과 토폴로지 조회")
    void getSensorList_Success() throws Exception {
        int roomId = 101;
        List<SensorInfoResponse> responses = List.of(
                new SensorInfoResponse(roomId, "devEui1", "온습도센서", Map.of("temperature", "°C", "humidity", "%")),
                new SensorInfoResponse(roomId, "devEui2", "CO2센서", Map.of("co2", "ppm"))
        );

        when(sensorDeviceService.getSensorTopologyByRoomId(roomId)).thenReturn(responses);

        mockMvc.perform(get("/api/processing/sensors")
                        .param("roomId", String.valueOf(roomId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].devEui").value("devEui1"))
                .andExpect(jsonPath("$[0].deviceName").value("온습도센서"))
                .andExpect(jsonPath("$[0].measurement.temperature").value("°C"))
                .andExpect(jsonPath("$[1].devEui").value("devEui2"));

        verify(sensorDeviceService).getSensorTopologyByRoomId(roomId);
    }

    @Test
    @DisplayName("특정 devEui에 해당하는 메트릭 타입 목록 반환")
    void getMetricTypeList_Success() throws Exception {
        String devEui = "devEui123";
        List<MetricTypeResponse> metricTypes = List.of(
                new MetricTypeResponse("temperature", "온도", "GAUGE", "ACTIVE", "섭씨 온도", "Cel", "섭씨", "°C")
        );

        when(sensorDeviceService.getMetricTypesByDevEui(devEui)).thenReturn(metricTypes);

        mockMvc.perform(get("/api/processing/metric_type")
                        .param("devEui", devEui)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devEui123").isArray())
                .andExpect(jsonPath("$.devEui123[0].metricCode").value("temperature"))
                .andExpect(jsonPath("$.devEui123[0].symbol").value("°C"));

        verify(sensorDeviceService).getMetricTypesByDevEui(devEui);
    }

    @Test
    @DisplayName("전체 메트릭 카탈로그 목록 조회")
    void getMetricCatalog_Success() throws Exception {
        List<MetricTypeResponse> catalog = List.of(
                new MetricTypeResponse("co2", "이산화탄소", "GAUGE", "ACTIVE", "농도", "[ppm]", "백만분율", "ppm"),
                new MetricTypeResponse("temperature", "온도", "GAUGE", "ACTIVE", "섭씨", "Cel", "섭씨", "°C")
        );

        when(sensorDeviceService.getAllMetricCatalog()).thenReturn(catalog);

        mockMvc.perform(get("/api/processing/internal/metric-catalog")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].metricCode").value("co2"))
                .andExpect(jsonPath("$[1].metricCode").value("temperature"));

        verify(sensorDeviceService).getAllMetricCatalog();
    }

    @Test
    @DisplayName("다수의 devEui 리스트를 받아 해당하는 메트릭 타입 목록 조회")
    void getMetricTypeCatalog_Batch_Success() throws Exception {
        List<String> devEuis = List.of("dev1", "dev2");
        SensorBatchRequest request = new SensorBatchRequest(devEuis);

        Map<String, List<MetricTypeResponse>> responseMap = Map.of(
                "dev1", List.of(new MetricTypeResponse("co2", "이산화탄소", "GAUGE", "ACTIVE", "농도", "[ppm]", "백만분율", "ppm")),
                "dev2", List.of(new MetricTypeResponse("temperature", "온도", "GAUGE", "ACTIVE", "섭씨", "Cel", "섭씨", "°C"))
        );

        when(sensorDeviceService.getMetricTypesByDevEuis(devEuis)).thenReturn(responseMap);

        mockMvc.perform(post("/api/processing/internal/sensors/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dev1").isArray())
                .andExpect(jsonPath("$.dev1[0].metricCode").value("co2"))
                .andExpect(jsonPath("$.dev2").isArray())
                .andExpect(jsonPath("$.dev2[0].metricCode").value("temperature"));

        verify(sensorDeviceService).getMetricTypesByDevEuis(anyList());
    }
}