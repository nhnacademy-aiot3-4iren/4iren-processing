package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.sensor.RoomAssignmentResult;
import com.nhnacademy.processing.dto.sensor.SensorRoomAssignmentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("중복 호출 시 registerDeviceIfAbsent는 한 번만 수행 (캐싱)")
    void ensureRegistered_DeviceCaching() {
        DeviceIdentity device = new DeviceIdentity("app1", "app", "prof1", "dev1", DEV_EUI, ROOM_ID, "location", "point");
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(), Instant.now());
        when(sensorDeviceService.loadKnownMeasurements(DEV_EUI, BROKER_ID)).thenReturn(Set.of());

        registry.ensureRegistered(message, BROKER_ID);
        registry.ensureRegistered(message, BROKER_ID);

        verify(sensorDeviceService, times(1)).registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);
    }

    @Test
    @DisplayName("ENVIRONMENT 카테고리만 저장 시도")
    void ensureRegistered_CategoryFiltering() {
        DeviceIdentity device = new DeviceIdentity("app1", "app", "prof1", "dev1", DEV_EUI, ROOM_ID, "location", "point");
        SensorData co2Data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 800.0);
        SensorData batteryData = new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 100.0);
        SensorData rssiData = new SensorData(MeasurementCategory.NETWORK_QUALITY, "rssi", -60.0);

        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(co2Data, batteryData, rssiData), Instant.now());
        when(sensorDeviceService.loadKnownMeasurements(DEV_EUI, BROKER_ID)).thenReturn(Set.of());

        registry.ensureRegistered(message, BROKER_ID);

        verify(sensorDeviceService, times(1)).registerMeasurement(eq(DEV_EUI), eq(BROKER_ID), eq(co2Data), any());
        verify(sensorDeviceService, never()).registerMeasurement(eq(DEV_EUI), eq(BROKER_ID), eq(batteryData), any());
    }

    @Test
    @DisplayName("devEui나 brokerId가 null이면 resolveRoomId는 null을 반환한다")
    void resolveRoomId_NullParams() {
        assertThat(registry.resolveRoomId(null, BROKER_ID)).isNull();
        assertThat(registry.resolveRoomId(DEV_EUI, null)).isNull();
    }

    @Test
    @DisplayName("캐시에 없으면 DB를 조회하여 resolveRoomId 반환")
    void resolveRoomId_CacheMiss() {
        when(sensorDeviceService.findRoomId(DEV_EUI, BROKER_ID)).thenReturn(ROOM_ID);

        Integer roomId = registry.resolveRoomId(DEV_EUI, BROKER_ID);
        assertThat(roomId).isEqualTo(ROOM_ID);

        // 두 번째 호출은 캐시 사용 확인
        Integer cachedRoomId = registry.resolveRoomId(DEV_EUI, BROKER_ID);
        assertThat(cachedRoomId).isEqualTo(ROOM_ID);

        // Service는 1번만 호출되어야 함
        verify(sensorDeviceService, times(1)).findRoomId(DEV_EUI, BROKER_ID);
    }

    @Test
    @DisplayName("캐시에 없는데 DB에서도 null이면 resolveRoomId는 null 반환")
    void resolveRoomId_CacheMiss_DbNull() {
        when(sensorDeviceService.findRoomId(DEV_EUI, BROKER_ID)).thenReturn(null);

        Integer roomId = registry.resolveRoomId(DEV_EUI, BROKER_ID);

        assertThat(roomId).isNull();
        verify(sensorDeviceService, times(1)).findRoomId(DEV_EUI, BROKER_ID);
    }

    @Test
    @DisplayName("assignRoomsAndEvictCache 호출 시 캐시에 저장된다")
    void assignRoomsAndEvictCache_Success() {
        SensorRoomAssignmentRequest request = new SensorRoomAssignmentRequest(DEV_EUI, 101L, 202);
        RoomAssignmentResult result = new RoomAssignmentResult(DEV_EUI, BROKER_ID, 202);

        when(sensorDeviceService.assignRooms(anyList())).thenReturn(List.of(result));

        registry.assignRoomsAndEvictCache(List.of(request));

        verify(sensorDeviceService).assignRooms(anyList());
        assertThat(registry.resolveRoomId(DEV_EUI, BROKER_ID)).isEqualTo(202);
    }

    @Test
    @DisplayName("unassignRoomAndEvictCache 호출 시 캐시가 비워진다(Optional.empty)")
    void unassignRoomAndEvictCache_Success() {
        RoomAssignmentResult result = new RoomAssignmentResult(DEV_EUI, BROKER_ID, null);
        when(sensorDeviceService.unassignRoom(ROOM_ID)).thenReturn(List.of(result));

        registry.unassignRoomAndEvictCache(ROOM_ID);

        verify(sensorDeviceService).unassignRoom(ROOM_ID);
        assertThat(registry.resolveRoomId(DEV_EUI, BROKER_ID)).isNull();
    }
}