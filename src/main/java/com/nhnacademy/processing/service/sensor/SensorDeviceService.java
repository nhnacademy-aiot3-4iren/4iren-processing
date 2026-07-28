package com.nhnacademy.processing.service.sensor;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.domain.SensorDevice;
import com.nhnacademy.processing.domain.SensorMeasurement;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.repository.MeasurementTypeRepository;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import com.nhnacademy.processing.repository.SensorDeviceRepository;
import com.nhnacademy.processing.repository.SensorMeasurementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final MqttBrokerInfoRepository mqttBrokerInfoRepository;
    private final MeasurementTypeRepository measurementTypeRepository;
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
                    roomId
            );

            sensorDeviceRepository.save(entity);
            log.info("신규 센서 기기 등록 완료: devEui({}), deviceName({}), roomId({})", devEui, deviceIdentity.deviceName(), roomId);
        } catch (DataIntegrityViolationException e) {
            log.debug("센서 기기 동시 등록 경합 발생 (무시 가능): devEui({})", devEui);
        }
    }

    @Transactional
    public void registerMeasurement(String devEui, SensorData data, Set<String> known) {
        measurementTypeRepository.findByName(data.measurement()).ifPresentOrElse(type -> {
            try {
                SensorDevice deviceProxy = sensorDeviceRepository.getReferenceById(devEui);
                SensorMeasurement measurement = new SensorMeasurement(deviceProxy, type, true);

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
        sensorMeasurementRepository.findAllBySensorDevice_DevEui(devEui)
                .forEach(m -> set.add(m.getMeasurementType().getName()));
        return set;
    }
}
