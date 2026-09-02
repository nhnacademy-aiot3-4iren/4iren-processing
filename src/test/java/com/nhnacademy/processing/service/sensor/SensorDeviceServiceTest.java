package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.domain.*;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.sensor.*;
import com.nhnacademy.processing.repository.MetricTypeRepository;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import com.nhnacademy.processing.repository.SensorDeviceRepository;
import com.nhnacademy.processing.repository.SensorMeasurementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDeviceServiceTest {
    @Mock private SensorDeviceRepository sensorDeviceRepository;
    @Mock private MqttBrokerInfoRepository mqttBrokerInfoRepository;
    @Mock private MetricTypeRepository metricTypeRepository;
    @Mock private SensorMeasurementRepository sensorMeasurementRepository;

    @InjectMocks
    private SensorDeviceService service;

    private static final String DEV_EUI = "123456789abcdefg";
    private static final Long BROKER_ID = 1L;
    private static final int ROOM_ID = 11;
    private static final Long BUILDING_ID = 101L;

    private ParsedSensorMessage createMessage() {
        return new ParsedSensorMessage(
                new DeviceIdentity(
                        "applicationID", "applicationName",
                        "deviceProfileId", "deviceName",
                        "devEui", ROOM_ID, "location", "point"
                ),
                List.of(), Instant.now()
        );
    }

    // -- 기존 테스트 유지 --
    @Test
    @DisplayName("DB에 devEui가 없을 때 sensor_devices에 저장한다")
    void registerNewDevice_Success() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(mock(MqttBrokerInfo.class));

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);
        verify(sensorDeviceRepository, times(1)).save(any(SensorDevice.class));
    }

    @Test
    @DisplayName("DB에 devEui가 존재하면 저장을 건너뛴다")
    void registerDevice_AlreadyExists_SkipSave() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(true);

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);
        verify(sensorDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시성 문제로 DataIntegrityViolationException이 발생해도 예외를 먹고 진행한다")
    void registerDevice_ConcurrencyConflict() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(mock(MqttBrokerInfo.class));
        doThrow(new DataIntegrityViolationException("Duplicate key")).when(sensorDeviceRepository).save(any(SensorDevice.class));

        assertThatCode(() -> service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("metric_types에 존재할 때 sensor_measurements에 저장한다")
    void registerMeasurement_Success() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0);
        Set<String> known = new HashSet<>();

        MeasurementUnit unit = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType type = new MetricType(1L, unit, "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");
        SensorDevice mockDevice = mock(SensorDevice.class);

        when(metricTypeRepository.findByCode("co2")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID))
                .thenReturn(Optional.of(mockDevice));

        service.registerMeasurement(DEV_EUI, BROKER_ID, data, known);

        verify(sensorMeasurementRepository, times(1)).save(any(SensorMeasurement.class));
        assertThat(known).contains("co2");
    }

    @Test
    @DisplayName("metric_types에 존재하지 않는 측정이면 저장하지 않는다")
    void registerMeasurement_UnknownType() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "unknown", 999.0);
        Set<String> known = new HashSet<>();

        when(metricTypeRepository.findByCode("unknown")).thenReturn(Optional.empty());

        service.registerMeasurement(DEV_EUI, BROKER_ID, data, known);

        verify(sensorDeviceRepository, never()).findByDevEuiAndMqttBrokerInfo_Id(any(), any());
        verify(sensorMeasurementRepository, never()).save(any());
        assertThat(known).doesNotContain("unknown");
    }

    @Test
    @DisplayName("알려진 측정항목 불러오기")
    void loadKnownMeasurements_Success() {
        SensorDevice device = mock(SensorDevice.class);
        MeasurementUnit unit1 = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType co2Type = new MetricType(1L, unit1, "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");
        MeasurementUnit unit2 = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType tempType = new MetricType(2L, unit2, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "temp");

        SensorMeasurement m1 = new SensorMeasurement(device, co2Type);
        SensorMeasurement m2 = new SensorMeasurement(device, tempType);

        when(sensorMeasurementRepository.findAllByDevEuiWithMeasurementType(DEV_EUI, BROKER_ID))
                .thenReturn(List.of(m1, m2));

        Set<String> result = service.loadKnownMeasurements(DEV_EUI, BROKER_ID);
        assertThat(result).containsExactlyInAnyOrder("co2", "temperature");
    }

    @Test
    @DisplayName("devEui와 brokerId로 roomId를 조회한다")
    void findRoomId_Success() {
        when(sensorDeviceRepository.findRoomIdOnly(DEV_EUI, BROKER_ID)).thenReturn(Optional.of(ROOM_ID));
        Integer result = service.findRoomId(DEV_EUI, BROKER_ID);
        assertThat(result).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("방 번호 할당 요청 리스트를 받아 RoomAssignmentResult로 매핑한다")
    void assignRooms_Success() {
        SensorRoomAssignmentRequest request = new SensorRoomAssignmentRequest(DEV_EUI, BUILDING_ID, 202);
        MqttBrokerInfo mockBroker = mock(MqttBrokerInfo.class);
        when(mockBroker.getId()).thenReturn(BROKER_ID);

        SensorDevice device = new SensorDevice(DEV_EUI, mockBroker, "appId", "appName", "profile1", "온도센서", null, "회의실", "창가");

        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_BuildingId(DEV_EUI, BUILDING_ID))
                .thenReturn(Optional.of(device));

        List<RoomAssignmentResult> results = service.assignRooms(List.of(request));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().devEui()).isEqualTo(DEV_EUI);
        assertThat(results.getFirst().brokerId()).isEqualTo(BROKER_ID);
        assertThat(results.getFirst().roomId()).isEqualTo(202);
        assertThat(device.getRoomId()).isEqualTo(202);
    }

    @Test
    @DisplayName("방 번호로 unassignRoom 호출 시 SensorDevice의 roomId가 null로 변경된다")
    void unassignRoom_Success() {
        MqttBrokerInfo mockBroker = mock(MqttBrokerInfo.class);
        when(mockBroker.getId()).thenReturn(BROKER_ID);
        SensorDevice device1 = new SensorDevice(DEV_EUI, mockBroker, "appId", "appName", "profile1", "dev1", ROOM_ID, "loc", "pt");

        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of(device1));

        List<RoomAssignmentResult> results = service.unassignRoom(ROOM_ID);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().roomId()).isNull();
        assertThat(device1.getRoomId()).isNull();
    }

    @Test
    @DisplayName("방 번호로 센서 목록 조회")
    void getSensorsByRoomId_Success() {
        SensorDevice device = new SensorDevice(DEV_EUI, mock(MqttBrokerInfo.class), "appId", "appName", "profile1", "dev1", ROOM_ID, "loc", "pt");
        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of(device));

        List<SensorSummaryResponse> responses = service.getSensorsByRoomId(ROOM_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().roomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("buildingId로 센서 목록 조회")
    void getSensorsByBrokerId_Success() {
        SensorDevice device = new SensorDevice(DEV_EUI, mock(MqttBrokerInfo.class), "appId", "appName", "profile1", "온도센서", ROOM_ID, "회의실", "창가");
        when(sensorDeviceRepository.findAllByMqttBrokerInfo_BuildingId(BUILDING_ID)).thenReturn(List.of(device));

        List<SensorSummaryResponse> responses = service.getSensorsByBuildingId(BUILDING_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().devEui()).isEqualTo(DEV_EUI);
        assertThat(responses.getFirst().deviceName()).isEqualTo("온도센서");
    }
}