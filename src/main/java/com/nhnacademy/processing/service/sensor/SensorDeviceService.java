package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.domain.*;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.sensor.MetricTypeResponse;
import com.nhnacademy.processing.dto.sensor.SensorInfoResponse;
import com.nhnacademy.processing.repository.MetricTypeRepository;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import com.nhnacademy.processing.repository.SensorDeviceRepository;
import com.nhnacademy.processing.repository.SensorMeasurementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final MqttBrokerInfoRepository mqttBrokerInfoRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final SensorMeasurementRepository sensorMeasurementRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerDeviceIfAbsent(ParsedSensorMessage message, String devEui, Long brokerId, int roomId) {
        if(sensorDeviceRepository.existsById(devEui)) {
            return;
        }

        try {
            DeviceIdentity deviceIdentity = message.device();
            MqttBrokerInfo brokerProxy = mqttBrokerInfoRepository.getReferenceById(brokerId);

            SensorDevice entity = new SensorDevice(
                    devEui,
                    brokerProxy,
                    deviceIdentity.applicationId(),
                    deviceIdentity.applicationName(),
                    deviceIdentity.deviceProfileId(),
                    deviceIdentity.deviceName(),
                    roomId,
                    deviceIdentity.point()
            );

            sensorDeviceRepository.save(entity);
            log.info("신규 센서 기기 등록 완료: devEui({}), deviceName({}), roomId({})", devEui, deviceIdentity.deviceName(), roomId);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
                log.debug("센서 기기 동시 등록 경합 발생 (무시 가능): devEui({})", devEui);
        }
    }

    @Transactional
    public void registerMeasurement(String devEui, SensorData data, Set<String> known) {
        metricTypeRepository.findByCode(data.measurement()).ifPresentOrElse(type -> {
            try {
                SensorDevice deviceProxy = sensorDeviceRepository.getReferenceById(devEui);
                SensorMeasurement measurement = new SensorMeasurement(deviceProxy, type);

                sensorMeasurementRepository.save(measurement);
                known.add(data.measurement());
                log.info("센서 측정항목 연결 완료: devEui={}, measurement({})(ID:{})", devEui, data.measurement(), type.getId());
            } catch (DataIntegrityViolationException e) {
                known.add(data.measurement());
            }
        }, () -> log.warn("measurement_types 테이블에 정의되지 않은 측정항목: {}", data.measurement()));
    }

    @Transactional(readOnly = true)
    public Set<String> loadKnownMeasurements(String devEui) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        sensorMeasurementRepository.findAllByDevEuiWithMeasurementType(devEui)
                .forEach(m -> set.add(m.getMeasurementType().getCode()));
        return set;
    }

    @Transactional(readOnly = true)
    public List<SensorInfoResponse> getSensorTopologyByRoomId(int roomId) {
        List<SensorDevice> devices = sensorDeviceRepository.findAllByRoomId(roomId);
        List<SensorMeasurement> measurements = sensorMeasurementRepository.findAllActiveMeasurementsByRoomId(roomId);

        return mapToDtoList(devices, measurements);
    }

    @Transactional(readOnly = true)
    public List<MetricTypeResponse> getMetricTypesByDevEui(String devEui) {
        List<SensorMeasurement> measurements =
                sensorMeasurementRepository.findAllByDevEuiWithMetricTypeAndUnit(devEui);

        return measurements.stream()
                .map(MetricTypeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MetricTypeResponse> getAllMetricCatalog() {
        return metricTypeRepository.findAllWithUnit().stream()
                .map(MetricTypeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, List<MetricTypeResponse>> getMetricTypesByDevEuis(List<String> devEuis) {
        if (devEuis == null || devEuis.isEmpty()) {
            return Collections.emptyMap();
        }

        List<SensorMeasurement> measurements =
                sensorMeasurementRepository.findAllByDevEuiInWithMetricTypeAndUnit(devEuis);

        return measurements.stream()
                .collect(Collectors.groupingBy(
                        sm -> sm.getSensorDevice().getDevEui(),
                        Collectors.mapping(MetricTypeResponse::from, Collectors.toList())
                ));
    }

    // ================== 내부 조립(Mapping) 로직 ==================
    private List<SensorInfoResponse> mapToDtoList(List<SensorDevice> devices, List<SensorMeasurement> measurements) {
        Map<String, Map<String, String>> measurementsByDevEui = measurements.stream()
                .collect(Collectors.groupingBy(
                        sm -> sm.getSensorDevice().getDevEui(),
                        Collectors.toMap(
                                sm -> sm.getMeasurementType().getCode(),
                                sm -> {
                                    var unit = sm.getMeasurementType().getUnit();
                                    return unit != null ? unit.getSymbol() : ""; // 단위가 없으면 빈 문자열
                                },
                                (existing, replacement) -> existing // 중복 키 충돌 방지
                        )
                ));

        // 2. SensorDevice 정보를 DTO로 변환하여 리스트로 반환
        return devices.stream()
                .map(device -> new SensorInfoResponse(
                        device.getRoomId(), // 새로 추가된 roomId 매핑
                        device.getDevEui(),
                        device.getDeviceName(),
                        measurementsByDevEui.getOrDefault(device.getDevEui(), Collections.emptyMap())
                ))
                .toList();
    }
}
