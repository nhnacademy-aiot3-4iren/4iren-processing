package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDeviceRegistryTest {
    @Mock
    private SensorDeviceService sensorDeviceService;

    @InjectMocks
    private SensorDeviceRegistry registry;

    private static final String DEV_EUI = "123456789abcdefg";
    private static final Long BROKER_ID = 1L;
    private static final int ROOM_ID = 11;

    @Test
    @DisplayName("처음 수신된 메시지는 registerDeviceIfAbsent 호출하고 두번 수신되면 재시도 안함")
    void ensureRegistered_DeviceCaching() {
        // given
        DeviceIdentity device = new DeviceIdentity("app1", "app", "prof1", "dev1", DEV_EUI, ROOM_ID, "location", "point");
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(), Instant.now());

        when(sensorDeviceService.loadKnownMeasurements(DEV_EUI)).thenReturn(Set.of());

        // when 1차 수신
        registry.ensureRegistered(message, BROKER_ID);

        // when 2차 수신 (동일 devEui)
        registry.ensureRegistered(message, BROKER_ID);

        // then: registerDeviceIfAbsent는 1번만 실행되어야 함
        verify(sensorDeviceService, times(1)).registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);
    }

    @Test
    @DisplayName("ENVIRONMENT 카테고리만 측정항목 저장 대상으로 전달되고 나머지는 걸러짐")
    void ensureRegistered_CategoryFiltering() {
        // given
        DeviceIdentity device = new DeviceIdentity("app1", "app", "prof1", "dev1", DEV_EUI, ROOM_ID, "location", "point");
        SensorData co2Data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 800.0);
        SensorData batteryData = new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 100.0);
        SensorData rssiData = new SensorData(MeasurementCategory.NETWORK_QUALITY, "rssi", -60.0);

        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(co2Data, batteryData, rssiData), Instant.now());

        when(sensorDeviceService.loadKnownMeasurements(DEV_EUI)).thenReturn(Set.of());

        // when
        registry.ensureRegistered(message, BROKER_ID);

        // then: co2에 대해서만 registerMeasurement 호출
        verify(sensorDeviceService, times(1)).registerMeasurement(eq(DEV_EUI), eq(co2Data), any());
        verify(sensorDeviceService, never()).registerMeasurement(eq(DEV_EUI), eq(batteryData), any());
        verify(sensorDeviceService, never()).registerMeasurement(eq(DEV_EUI), eq(rssiData), any());
    }

    @Test
    @DisplayName("이미 저장된 항목은 registerMeasurement를 호출하지 않음")
    void ensureRegistered_MeasurementCaching() {
        // given
        DeviceIdentity device = new DeviceIdentity("app1", "app", "prof1", "dev1", DEV_EUI, ROOM_ID, "location", "point");
        SensorData co2Data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 800.0);
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(co2Data), Instant.now());

        // DB에서 이미 co2가 등록되어 있음으로 응답 설정
        when(sensorDeviceService.loadKnownMeasurements(DEV_EUI)).thenReturn(Set.of("co2"));

        // when
        registry.ensureRegistered(message, BROKER_ID);

        // then
        verify(sensorDeviceService, never()).registerMeasurement(any(), any(), any());
    }
}
