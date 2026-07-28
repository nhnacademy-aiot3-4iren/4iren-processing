package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.domain.*;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.repository.MeasurementTypeRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private MeasurementTypeRepository measurementTypeRepository;
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
                        "devEui", null, ROOM_ID
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

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID, ROOM_ID);

        verify(sensorDeviceRepository, times(1)).save(any(SensorDevice.class));
    }

    @Test
    @DisplayName("DB에 존재하는 devEui는 그냥 리턴")
    void registerDevice_AlreadyExists_SkipSave() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsById(DEV_EUI)).thenReturn(true);

        service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID, ROOM_ID);

        verify(sensorDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시성 경합 발생하도 중지되지 않음")
    void registerDevice_ConcurrencyConflict() {
        ParsedSensorMessage message = createMessage();
        when(sensorDeviceRepository.existsById(DEV_EUI)).thenReturn(false);
        when(mqttBrokerInfoRepository.getReferenceById(BROKER_ID)).thenReturn(mock(MqttBrokerInfo.class));
        doThrow(new DataIntegrityViolationException("Duplicate key")).when(sensorDeviceRepository).save(any(SensorDevice.class));

        assertThatCode(() -> service.registerDeviceIfAbsent(message, DEV_EUI, BROKER_ID, ROOM_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("measurement_types에 존재하는 측정항목이면 sensor_measurements에 저장하고 캐싱")
    void registerMeasurement_Success() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0);
        Set<String> known = new HashSet<>();
        MeasurementType type = new MeasurementType("co2", UnitType.PPM);

        when(measurementTypeRepository.findByName("co2")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.getReferenceById(DEV_EUI)).thenReturn(mock(SensorDevice.class));

        service.registerMeasurement(DEV_EUI, data, known);

        verify(sensorMeasurementRepository, times(1)).save(any(SensorMeasurement.class));
        assertThat(known).contains("co2");
    }

    @Test
    @DisplayName("measurement_types에 등록되지 않은 측정항목이면 저장 시도 안함")
    void registerMeasurement_UnknownType() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "unknown", 999.0);
        Set<String> known = new HashSet<>();

        when(measurementTypeRepository.findByName("unknown")).thenReturn(Optional.empty());

        service.registerMeasurement(DEV_EUI, data, known);

        verify(sensorMeasurementRepository, never()).save(any());
        assertThat(known).doesNotContain("unknown");
    }

    @Test
    @DisplayName("동시성 경합 발생해도 중단되지 않음")
    void registerMeasurement_ConcurrencyConflict() {
        SensorData data = new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0);
        Set<String> known = new HashSet<>();
        MeasurementType type = new MeasurementType("co2", UnitType.PPM);

        when(measurementTypeRepository.findByName("co2")).thenReturn(Optional.of(type));
        when(sensorDeviceRepository.getReferenceById(DEV_EUI)).thenReturn(mock(SensorDevice.class));
        doThrow(new DataIntegrityViolationException("Duplicate Key")).when(sensorMeasurementRepository).save(any(SensorMeasurement.class));

        assertThatCode(() -> service.registerMeasurement(DEV_EUI, data, known))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("특정 devEui에 연관된 기존 측정항목 이름 집합 반환")
    void loadKnownMeasurements_Success() {
        SensorDevice device = mock(SensorDevice.class);
        MeasurementType co2Type = new MeasurementType("co2", UnitType.PPM);
        MeasurementType tempType = new MeasurementType("temperature", UnitType.CELSIUS);

        SensorMeasurement m1 = new SensorMeasurement(device, co2Type, true);
        SensorMeasurement m2 = new SensorMeasurement(device, tempType, true);

        when(sensorMeasurementRepository.findAllBySensorDevice_DevEui(DEV_EUI)).thenReturn(List.of(m1, m2));

        Set<String> result = service.loadKnownMeasurements(DEV_EUI);

        assertThat(result).containsExactlyInAnyOrder("co2", "temperature");
    }
}
