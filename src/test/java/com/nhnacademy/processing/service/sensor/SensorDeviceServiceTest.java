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
import org.springframework.dao.PessimisticLockingFailureException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
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
    private static final int ROOM_ID = 101;
    private static final Long BUILDING_ID = 50L;

    private ParsedSensorMessage createMessage() {
        DeviceIdentity identity = new DeviceIdentity(
                "appId", "appName", "profId", "devName", DEV_EUI, null, "loc", "pt"
        );
        return new ParsedSensorMessage(identity, List.of(), Instant.now());
    }

    private MqttBrokerInfo createMockBroker() {
        return new MqttBrokerInfo(BROKER_ID, BUILDING_ID, "server", "tcp://host", "user", "pass", "topic");
    }

    private SensorDevice createMockDevice() {
        return new SensorDevice(DEV_EUI, createMockBroker(), "appId", "appName", "profId", "devName", ROOM_ID, "loc", "pt");
    }

    @Test
    @DisplayName("기기가 존재하지 않으면 새로 등록한다")
    void registerDeviceIfAbsent_Success() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(createMockBroker());

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);

        verify(sensorDeviceRepository, times(1)).save(any(SensorDevice.class));
    }

    @Test
    @DisplayName("기기가 이미 존재하면 등록을 건너뛴다")
    void registerDeviceIfAbsent_AlreadyExists() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(true);

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);

        verify(sensorDeviceRepository, never()).save(any(SensorDevice.class));
    }

    @Test
    @DisplayName("기기 등록 중 동시성 예외 발생 시 무시하고 정상 종료된다")
    void registerDeviceIfAbsent_ConcurrencyException() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(createMockBroker());
        doThrow(new PessimisticLockingFailureException("Lock Error"))
                .when(sensorDeviceRepository).save(any(SensorDevice.class));

        assertThatCode(() -> service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("측정 항목을 정상적으로 연결하고 known 셋에 추가한다")
    void registerMeasurement_Success() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0);
        Set<String> known = new HashSet<>();
        MetricType type = new MetricType(1L, mock(MeasurementUnit.class), "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");

        when(metricTypeRepository.findByCode("temperature")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID))
                .thenReturn(Optional.of(createMockDevice()));

        service.registerMeasurement(DEV_EUI, BROKER_ID, data, known);

        verify(sensorMeasurementRepository, times(1)).save(any(SensorMeasurement.class));
        assertThat(known).contains("temperature");
    }

    @Test
    @DisplayName("측정 항목 연결 중 중복(DataIntegrityViolationException) 발생 시 known 셋에만 추가하고 무시한다")
    void registerMeasurement_DuplicateException() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 400.0);
        Set<String> known = new HashSet<>();
        MetricType type = new MetricType(1L, mock(MeasurementUnit.class), "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");

        when(metricTypeRepository.findByCode("co2")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID))
                .thenReturn(Optional.of(createMockDevice()));
        doThrow(new DataIntegrityViolationException("Duplicate Key"))
                .when(sensorMeasurementRepository).save(any(SensorMeasurement.class));

        assertThatCode(() -> service.registerMeasurement(DEV_EUI, BROKER_ID, data, known))
                .doesNotThrowAnyException();

        assertThat(known).contains("co2");
    }

    @Test
    @DisplayName("존재하지 않는 측정 항목(MetricType)일 경우 경고 로그만 남기고 무시한다")
    void registerMeasurement_MetricTypeNotFound() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "unknown", 1.0);
        Set<String> known = new HashSet<>();

        when(metricTypeRepository.findByCode("unknown")).thenReturn(Optional.empty());

        service.registerMeasurement(DEV_EUI, BROKER_ID, data, known);

        verify(sensorDeviceRepository, never()).findByDevEuiAndMqttBrokerInfo_Id(anyString(), anyLong());
        verify(sensorMeasurementRepository, never()).save(any());
        assertThat(known).isEmpty();
    }

    @Test
    @DisplayName("측정 항목은 존재하지만 센서 기기가 없을 경우 경고 로그만 남긴다")
    void registerMeasurement_DeviceNotFound() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0);
        Set<String> known = new HashSet<>();
        MetricType type = new MetricType(1L, mock(MeasurementUnit.class), "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");

        when(metricTypeRepository.findByCode("temperature")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_Id(DEV_EUI, BROKER_ID)).thenReturn(Optional.empty());

        service.registerMeasurement(DEV_EUI, BROKER_ID, data, known);

        verify(sensorMeasurementRepository, never()).save(any());
        assertThat(known).isEmpty();
    }

    @Test
    @DisplayName("devEui로 알려진 측정 항목(Set)을 로드한다")
    void loadKnownMeasurements_Success() {
        MetricType type = new MetricType(1L, mock(MeasurementUnit.class), "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");
        SensorMeasurement measurement = new SensorMeasurement(createMockDevice(), type);

        when(sensorMeasurementRepository.findAllByDevEuiWithMeasurementType(DEV_EUI, BROKER_ID))
                .thenReturn(List.of(measurement));

        Set<String> known = service.loadKnownMeasurements(DEV_EUI, BROKER_ID);

        assertThat(known).containsExactly("temperature");
    }

    @Test
    @DisplayName("devEui와 brokerId로 배정된 roomId를 조회한다")
    void findRoomId_Success() {
        when(sensorDeviceRepository.findRoomIdOnly(DEV_EUI, BROKER_ID)).thenReturn(Optional.of(ROOM_ID));
        Integer result = service.findRoomId(DEV_EUI, BROKER_ID);
        assertThat(result).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("기기들에 RoomId를 정상적으로 배정한다")
    void assignRooms_Success() {
        SensorRoomAssignmentRequest req1 = new SensorRoomAssignmentRequest(DEV_EUI, BUILDING_ID, 202);
        SensorRoomAssignmentRequest req2 = new SensorRoomAssignmentRequest("not_found", BUILDING_ID, 202); // 없는 기기

        SensorDevice device = createMockDevice();
        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_BuildingId(DEV_EUI, BUILDING_ID))
                .thenReturn(Optional.of(device));
        when(sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_BuildingId("not_found", BUILDING_ID))
                .thenReturn(Optional.empty());

        List<RoomAssignmentResult> results = service.assignRooms(List.of(req1, req2));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).devEui()).isEqualTo(DEV_EUI);
        assertThat(results.get(0).roomId()).isEqualTo(202);
        assertThat(device.getRoomId()).isEqualTo(202); // 엔티티 내부 값 변경 확인
    }

    @Test
    @DisplayName("특정 룸에 속한 모든 센서 기기의 RoomId를 null로 초기화한다")
    void unassignRoom_Success() {
        SensorDevice device = createMockDevice();
        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of(device));

        List<RoomAssignmentResult> results = service.unassignRoom(ROOM_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).roomId()).isNull();
        assertThat(device.getRoomId()).isNull(); // 엔티티 내부 값 변경 확인
    }

    @Test
    @DisplayName("조회 메서드들이 올바르게 DTO로 매핑하여 반환한다")
    void readQueries_MappingSuccess() {
        SensorDevice device = createMockDevice();
        when(sensorDeviceRepository.findAllByMqttBrokerInfo_BuildingId(BUILDING_ID)).thenReturn(List.of(device));
        when(sensorDeviceRepository.findAllByMqttBrokerInfo_BuildingIdAndRoomIdIsNull(BUILDING_ID)).thenReturn(List.of(device));
        when(sensorDeviceRepository.findAllByMqttBrokerInfo_BuildingIdAndRoomId(BUILDING_ID, ROOM_ID)).thenReturn(List.of(device));
        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of(device));

        assertThat(service.getSensorsByBuildingId(BUILDING_ID)).hasSize(1);
        assertThat(service.getUnassignedSensorsByBuildingId(BUILDING_ID)).hasSize(1);
        assertThat(service.getSensorsByBuildingIdAndRoomId(BUILDING_ID, ROOM_ID)).hasSize(1);
        assertThat(service.getSensorsByRoomId(ROOM_ID)).hasSize(1);
    }

    @Test
    @DisplayName("Room ID를 기반으로 센서 토폴로지 정보(MapToDtoList 포함)를 정상적으로 구성한다")
    void getSensorTopologyByRoomId_Success() {
        SensorDevice device = createMockDevice();

        // 단위(Unit)가 있는 측정 항목
        MeasurementUnit unit = new MeasurementUnit(1L, "Cel", "섭씨", "°C");
        MetricType typeWithUnit = new MetricType(1L, unit, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");
        SensorMeasurement measurement1 = new SensorMeasurement(device, typeWithUnit);

        // 단위(Unit)가 없는(Null) 측정 항목 커버리지
        MetricType typeWithoutUnit = new MetricType(2L, null, "door", "문열림", MetricKind.STATE, MetricTypeStatus.ACTIVE, "desc");
        SensorMeasurement measurement2 = new SensorMeasurement(device, typeWithoutUnit);

        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of(device));
        when(sensorMeasurementRepository.findAllActiveMeasurementsByRoomId(ROOM_ID))
                .thenReturn(List.of(measurement1, measurement2));

        List<SensorInfoResponse> topology = service.getSensorTopologyByRoomId(ROOM_ID);

        assertThat(topology).hasSize(1);
        SensorInfoResponse response = topology.get(0);
        assertThat(response.devEui()).isEqualTo(DEV_EUI);

        Map<String, String> metrics = response.measurement();
        assertThat(metrics).containsEntry("temperature", "°C");
        assertThat(metrics).containsEntry("door", ""); // Unit이 Null일 때 빈 문자열 매핑 확인
    }

    @Test
    @DisplayName("특정 센서의 메트릭 카탈로그를 조회한다")
    void getMetricTypesByDevEui_Success() {
        MetricType type = new MetricType(1L, new MeasurementUnit(1L, "Cel", "섭씨", "°C"), "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");
        SensorMeasurement measurement = new SensorMeasurement(createMockDevice(), type);

        when(sensorMeasurementRepository.findAllByDevEuiWithMetricTypeAndUnit(DEV_EUI)).thenReturn(List.of(measurement));

        List<MetricTypeResponse> responses = service.getMetricTypesByDevEui(DEV_EUI);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).metricCode()).isEqualTo("temperature");
    }

    @Test
    @DisplayName("전체 메트릭 카탈로그를 조회한다")
    void getAllMetricCatalog_Success() {
        MetricType type = new MetricType(1L, new MeasurementUnit(1L, "Cel", "섭씨", "°C"), "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");

        when(metricTypeRepository.findAllWithUnit()).thenReturn(List.of(type));

        List<MetricTypeResponse> responses = service.getAllMetricCatalog();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).metricCode()).isEqualTo("temperature");
    }

    @Test
    @DisplayName("다건의 DevEui에 대해 매핑된 메트릭 정보를 그룹화하여 반환한다")
    void getMetricTypesByDevEuis_Success() {
        String devEui2 = "otherEui";
        SensorDevice device1 = createMockDevice();
        SensorDevice device2 = new SensorDevice(devEui2, createMockBroker(), "app", "app", "prof", "dev", ROOM_ID, "loc", "pt");

        MetricType type = new MetricType(1L, new MeasurementUnit(1L, "Cel", "섭씨", "°C"), "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "desc");
        SensorMeasurement m1 = new SensorMeasurement(device1, type);
        SensorMeasurement m2 = new SensorMeasurement(device2, type);

        when(sensorMeasurementRepository.findAllByDevEuiInWithMetricTypeAndUnit(List.of(DEV_EUI, devEui2)))
                .thenReturn(List.of(m1, m2));

        Map<String, List<MetricTypeResponse>> result = service.getMetricTypesByDevEuis(List.of(DEV_EUI, devEui2));

        assertThat(result).hasSize(2);
        assertThat(result.get(DEV_EUI)).hasSize(1);
        assertThat(result.get(devEui2)).hasSize(1);
    }

    @Test
    @DisplayName("DevEui 리스트가 null이거나 비어있으면 빈 Map을 즉시 반환한다")
    void getMetricTypesByDevEuis_NullOrEmpty() {
        assertThat(service.getMetricTypesByDevEuis(null)).isEmpty();
        assertThat(service.getMetricTypesByDevEuis(List.of())).isEmpty();

        verify(sensorMeasurementRepository, never()).findAllByDevEuiInWithMetricTypeAndUnit(any());
    }
}