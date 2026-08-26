package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.domain.*;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.sensor.MetricTypeResponse;
import com.nhnacademy.processing.dto.sensor.SensorInfoResponse;
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
    @Mock
    private SensorDeviceRepository sensorDeviceRepository;
    @Mock
    private MqttBrokerInfoRepository mqttBrokerInfoRepository;
    @Mock
    private MetricTypeRepository metricTypeRepository;
    @Mock
    private SensorMeasurementRepository sensorMeasurementRepository;

    @InjectMocks
    private SensorDeviceService service;

    private static final String DEV_EUI = "123456789abcdefg";
    private static final Long BROKER_ID = 1L;
    private static final int ROOM_ID = 11;

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

    @Test
    @DisplayName("DB에 존재하지 않는 devEui는 sensor_devices 테이블에 저장")
    void registerNewDevice_Success() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsById(DEV_EUI)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(mock(MqttBrokerInfo.class));

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);

        verify(sensorDeviceRepository, times(1)).save(any(SensorDevice.class));
    }

    @Test
    @DisplayName("DB에 존재하는 devEui는 그냥 리턴")
    void registerDevice_AlreadyExists_SkipSave() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsById(DEV_EUI)).thenReturn(true);

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID);

        verify(sensorDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시성 경합 발생해도 중지되지 않음")
    void registerDevice_ConcurrencyConflict() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsById(DEV_EUI)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(mock(MqttBrokerInfo.class));
        doThrow(new DataIntegrityViolationException("Duplicate key")).when(sensorDeviceRepository).save(any(SensorDevice.class));

        assertThatCode(() -> service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("metric_types에 존재하는 측정항목이면 sensor_measurements에 저장하고 캐싱")
    void registerMeasurement_Success() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0);
        Set<String> known = new HashSet<>();
        MeasurementUnit unit = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType type = new MetricType(1L, unit, "co2", "이산화탄소 농도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");

        when(metricTypeRepository.findByCode("co2")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.getReferenceById(DEV_EUI)).thenReturn(mock(SensorDevice.class));

        service.registerMeasurement(DEV_EUI, data, known);

        verify(sensorMeasurementRepository, times(1)).save(any(SensorMeasurement.class));
        assertThat(known).contains("co2");
    }

    @Test
    @DisplayName("metric_types에 등록되지 않은 측정항목이면 저장 시도 안함")
    void registerMeasurement_UnknownType() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "unknown", 999.0);
        Set<String> known = new HashSet<>();

        when(metricTypeRepository.findByCode("unknown")).thenReturn(Optional.empty());

        service.registerMeasurement(DEV_EUI, data, known);

        verify(sensorMeasurementRepository, never()).save(any());
        assertThat(known).doesNotContain("unknown");
    }

    @Test
    @DisplayName("동시성 경합 발생해도 중단되지 않음")
    void registerMeasurement_ConcurrencyConflict() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0);
        Set<String> known = new HashSet<>();
        MeasurementUnit unit = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType type = new MetricType(1L, unit, "co2", "이산화탄소 농도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");

        when(metricTypeRepository.findByCode("co2")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.getReferenceById(DEV_EUI)).thenReturn(mock(SensorDevice.class));
        doThrow(new DataIntegrityViolationException("Duplicate Key")).when(sensorMeasurementRepository).save(any(SensorMeasurement.class));

        assertThatCode(() -> service.registerMeasurement(DEV_EUI, data, known))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("특정 devEui에 연관된 기존 측정항목 이름 집합 반환")
    void loadKnownMeasurements_Success() {
        SensorDevice device = mock(SensorDevice.class);
        MeasurementUnit unit1 = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType co2Type = new MetricType(1L, unit1, "co2", "이산화탄소 농도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");

        MeasurementUnit unit2 = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType tempType = new MetricType(2L, unit2, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "temp");

        SensorMeasurement m1 = new SensorMeasurement(device, co2Type);
        SensorMeasurement m2 = new SensorMeasurement(device, tempType);

        when(sensorMeasurementRepository.findAllByDevEuiWithMeasurementType(DEV_EUI)).thenReturn(List.of(m1, m2));

        Set<String> result = service.loadKnownMeasurements(DEV_EUI);

        assertThat(result).containsExactlyInAnyOrder("co2", "temperature");
    }

    @Test
    @DisplayName("특정 roomId의 센서 토폴로지(장치 목록 및 측정항목 기호) 정상 조회")
    void getSensorTopologyByRoomId_Success() {
        MqttBrokerInfo mockBroker = mock(MqttBrokerInfo.class);
        SensorDevice device1 = new SensorDevice("dev1", mockBroker, "appId", "appName", "profile1", "온습도센서", ROOM_ID, "강의실", "전면");
        SensorDevice device2 = new SensorDevice("dev2", mockBroker, "appId", "appName", "profile2", "CO2센서", ROOM_ID, "강의실","후면");

        MeasurementUnit unitPpm = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType co2Type = new MetricType(1L, unitPpm, "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");

        MeasurementUnit unitCel = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType tempType = new MetricType(2L, unitCel, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "temp");

        SensorMeasurement m1 = new SensorMeasurement(1L, device1, co2Type, true);
        SensorMeasurement m2 = new SensorMeasurement(2L, device1, tempType, true);

        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of(device1, device2));
        when(sensorMeasurementRepository.findAllActiveMeasurementsByRoomId(ROOM_ID)).thenReturn(List.of(m1, m2));

        List<SensorInfoResponse> responses = service.getSensorTopologyByRoomId(ROOM_ID);

        assertThat(responses).hasSize(2);

        SensorInfoResponse response1 = responses.stream()
                .filter(r -> r.devEui().equals("dev1"))
                .findFirst()
                .orElseThrow();
        assertThat(response1.roomId()).isEqualTo(ROOM_ID);
        assertThat(response1.deviceName()).isEqualTo("온습도센서");
        assertThat(response1.measurement())
                .hasSize(2)
                .containsEntry("co2", "ppm")
                .containsEntry("temperature", "°C");

        SensorInfoResponse response2 = responses.stream()
                .filter(r -> r.devEui().equals("dev2"))
                .findFirst()
                .orElseThrow();
        assertThat(response2.deviceName()).isEqualTo("CO2센서");
        assertThat(response2.measurement()).isEmpty();
    }

    @Test
    @DisplayName("해당 roomId에 등록된 센서 장치가 없으면 빈 리스트 반환")
    void getSensorTopologyByRoomId_EmptyDevices() {
        when(sensorDeviceRepository.findAllByRoomId(ROOM_ID)).thenReturn(List.of());
        when(sensorMeasurementRepository.findAllActiveMeasurementsByRoomId(ROOM_ID)).thenReturn(List.of());

        List<SensorInfoResponse> responses = service.getSensorTopologyByRoomId(ROOM_ID);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("특정 devEui에 연관된 메트릭 타입 및 단위 정보 목록 정상 조회")
    void getMetricTypesByDevEui_Success() {
        SensorDevice device = mock(SensorDevice.class);

        MeasurementUnit unitPpm = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType co2Type = new MetricType(1L, unitPpm, "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2 농도");

        MeasurementUnit unitCel = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType tempType = new MetricType(2L, unitCel, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "섭씨 온도");

        SensorMeasurement m1 = new SensorMeasurement(1L, device, co2Type, true);
        SensorMeasurement m2 = new SensorMeasurement(2L, device, tempType, true);

        when(sensorMeasurementRepository.findAllByDevEuiWithMetricTypeAndUnit(DEV_EUI))
                .thenReturn(List.of(m1, m2));

        List<MetricTypeResponse> responses = service.getMetricTypesByDevEui(DEV_EUI);

        assertThat(responses).hasSize(2);

        MetricTypeResponse r1 = responses.getFirst();
        assertThat(r1.metricCode()).isEqualTo("co2");
        assertThat(r1.displayName()).isEqualTo("이산화탄소");
        assertThat(r1.metricKind()).isEqualTo("GAUGE");
        assertThat(r1.status()).isEqualTo("ACTIVE");
        assertThat(r1.description()).isEqualTo("co2 농도");
        assertThat(r1.ucumCode()).isEqualTo("[ppm]");
        assertThat(r1.unitDisplayName()).isEqualTo("백만분율");
        assertThat(r1.symbol()).isEqualTo("ppm");

        MetricTypeResponse r2 = responses.get(1);
        assertThat(r2.metricCode()).isEqualTo("temperature");
        assertThat(r2.symbol()).isEqualTo("°C");
    }

    @Test
    @DisplayName("특정 devEui에 해당하는 측정 항목이 없을 경우 빈 리스트 반환")
    void getMetricTypesByDevEui_Empty() {
        when(sensorMeasurementRepository.findAllByDevEuiWithMetricTypeAndUnit(DEV_EUI))
                .thenReturn(List.of());

        List<MetricTypeResponse> responses = service.getMetricTypesByDevEui(DEV_EUI);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("전체 메트릭 카탈로그 목록 정상 조회")
    void getAllMetricCatalog_Success() {
        MeasurementUnit unitPpm = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType co2Type = new MetricType(1L, unitPpm, "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");

        MeasurementUnit unitCel = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType tempType = new MetricType(2L, unitCel, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "temp");

        when(metricTypeRepository.findAllWithUnit()).thenReturn(List.of(co2Type, tempType));

        List<MetricTypeResponse> responses = service.getAllMetricCatalog();

        assertThat(responses).hasSize(2);

        MetricTypeResponse res1 = responses.getFirst();
        assertThat(res1.metricCode()).isEqualTo("co2");
        assertThat(res1.unitDisplayName()).isEqualTo("백만분율");

        MetricTypeResponse res2 = responses.get(1);
        assertThat(res2.metricCode()).isEqualTo("temperature");
        assertThat(res2.symbol()).isEqualTo("°C");
    }

    @Test
    @DisplayName("다수의 devEui 목록으로 메트릭 타입 정보 그룹 조회")
    void getMetricTypesByDevEuis_Success() {
        String devEui1 = "devEui1";
        String devEui2 = "devEui2";
        List<String> devEuis = List.of(devEui1, devEui2);

        SensorDevice device1 = mock(SensorDevice.class);
        when(device1.getDevEui()).thenReturn(devEui1);

        SensorDevice device2 = mock(SensorDevice.class);
        when(device2.getDevEui()).thenReturn(devEui2);

        MeasurementUnit unit1 = new MeasurementUnit(1L, "[ppm]", "백만분율", "ppm");
        MetricType co2Type = new MetricType(1L, unit1, "co2", "이산화탄소", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "co2");

        MeasurementUnit unit2 = new MeasurementUnit(2L, "Cel", "섭씨", "°C");
        MetricType tempType = new MetricType(2L, unit2, "temperature", "온도", MetricKind.GAUGE, MetricTypeStatus.ACTIVE, "temp");

        SensorMeasurement m1 = new SensorMeasurement(1L, device1, co2Type, true);
        SensorMeasurement m2 = new SensorMeasurement(2L, device1, tempType, true);
        SensorMeasurement m3 = new SensorMeasurement(3L, device2, co2Type, true); // device2는 co2만 측정

        when(sensorMeasurementRepository.findAllByDevEuiInWithMetricTypeAndUnit(devEuis))
                .thenReturn(List.of(m1, m2, m3));

        Map<String, List<MetricTypeResponse>> result = service.getMetricTypesByDevEuis(devEuis);

        assertThat(result).hasSize(2);

        assertThat(result.get(devEui1)).hasSize(2);
        assertThat(result.get(devEui1).stream().map(MetricTypeResponse::metricCode))
                .containsExactlyInAnyOrder("co2", "temperature");

        assertThat(result.get(devEui2)).hasSize(1);
        assertThat(result.get(devEui2).getFirst().metricCode()).isEqualTo("co2");
    }

    @Test
    @DisplayName("devEuis 리스트가 null이거나 비어있으면 빈 Map을 반환한다")
    void getMetricTypesByDevEuis_Empty() {
        // When
        Map<String, List<MetricTypeResponse>> resultEmpty = service.getMetricTypesByDevEuis(List.of());
        Map<String, List<MetricTypeResponse>> resultNull = service.getMetricTypesByDevEuis(null);

        // Then
        assertThat(resultEmpty).isEmpty();
        assertThat(resultNull).isEmpty();
        verify(sensorMeasurementRepository, never()).findAllByDevEuiInWithMetricTypeAndUnit(any());
    }
}