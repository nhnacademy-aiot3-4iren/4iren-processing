package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.domain.*;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.sensor.MetricTypeResponse;
import com.nhnacademy.processing.dto.sensor.SensorInfoResponse;
import com.nhnacademy.processing.dto.sensor.SensorRoomAssignmentRequest;
import com.nhnacademy.processing.dto.sensor.SensorSummaryResponse;
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

import java.util.*;
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
    public void registerDeviceIfAbsent(ParsedSensorMessage message, String devEui, Long brokerId) {
        if(sensorDeviceRepository.existsByDevEuiAndMqttBrokerInfo_Id(devEui, brokerId)) {
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
                    null,
                    deviceIdentity.location(),
                    deviceIdentity.point()
            );

            sensorDeviceRepository.save(entity);
            log.info("신규 센서 기기 등록 완료: devEui({}), deviceName({}), location({})", devEui, deviceIdentity.deviceName(), deviceIdentity.location());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            log.debug("센서 기기 동시 등록 경합 발생 (무시 가능): devEui({})", devEui);
        }
    }

    @Transactional
    public void registerMeasurement(String devEui, Long brokerId, SensorData data, Set<String> known) {
        metricTypeRepository.findByCode(data.measurement()).ifPresentOrElse(type ->
                        sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_Id(devEui, brokerId).ifPresentOrElse(device -> {
                            try {
                                SensorMeasurement measurement = new SensorMeasurement(device, type);

                                sensorMeasurementRepository.save(measurement);
                                known.add(data.measurement());
                                log.info("센서 측정항목 연결 완료: devEui={}, measurement({})(ID:{})", devEui, data.measurement(), type.getId());
                            } catch (DataIntegrityViolationException e) {
                                known.add(data.measurement());
                            }
                        }, () -> log.warn("등록되지 않은 센서 기기: devEui({}), brokerId({})", devEui, brokerId)),
                () -> log.warn("measurement_types 테이블에 정의되지 않은 측정항목: {}", data.measurement()));
    }

    @Transactional(readOnly = true)
    public Set<String> loadKnownMeasurements(String devEui, Long brokerId) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        sensorMeasurementRepository.findAllByDevEuiWithMeasurementType(devEui, brokerId)
                .forEach(m -> set.add(m.getMeasurementType().getCode()));
        return set;
    }

    // devEui와 brokerId로 배정된 roomId 조회
    @Transactional(readOnly = true)
    public Integer findRoomId(String devEui, Long brokerId) {
        return sensorDeviceRepository.findByDevEuiAndMqttBrokerInfo_Id(devEui, brokerId)
                .map(SensorDevice::getRoomId)
                .orElse(null);
    }

    // roomId 배정 후 갱신된 SensorDevice 목록 반환 (캐시 갱신용)
    @Transactional
    public List<SensorDevice> assignRooms(List<SensorRoomAssignmentRequest> requests) {
        return requests.stream()
                .map(request -> sensorDeviceRepository
                        .findByDevEuiAndMqttBrokerInfo_BuildingId(request.devEui(), request.buildingId())
                        .map(device -> {
                            device.assignRoom(request.roomId());
                            return device;
                        })
                        .orElseGet(() -> {
                            log.warn("roomId 매칭 대상 센서 기기를 찾을 수 없음: devEui({}), buildingId({})",
                                    request.devEui(), request.buildingId());
                            return null;
                        }))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SensorSummaryResponse> getSensorsByBuildingId(Long buildingId) {
        return sensorDeviceRepository.findAllByMqttBrokerInfo_BuildingId(buildingId).stream()
                .map(SensorSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SensorSummaryResponse> getSensorsByRoomId(Integer roomId) {
        return sensorDeviceRepository.findAllByRoomId(roomId).stream()
                .map(SensorSummaryResponse::from)
                .toList();
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
