package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import com.nhnacademy.processing.repository.SensorDeviceRepository;
import com.nhnacademy.processing.repository.SensorMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MqttBrokerService {

    private final MqttBrokerInfoRepository mqttBrokerInfoRepository;
    private final SensorMeasurementRepository sensorMeasurementRepository;
    private final SensorDeviceRepository sensorDeviceRepository;

    @Transactional(readOnly = true)
    public List<MqttBrokerInfoDto> getMqttBrokerInfo() {
        return mqttBrokerInfoRepository.findAllByEnabled(true).stream()
                .map(MqttBrokerInfoDto::from)
                .toList();
    }

    @Transactional
    public MqttBrokerInfoDto register(MqttBrokerCreateRequest request) {
        MqttBrokerInfo entity = new MqttBrokerInfo(
                request.buildingId(),
                request.serverName(),
                request.brokerUrl(),
                request.username(),
                request.password(),
                request.topic()
        );

        MqttBrokerInfo savedEntity = mqttBrokerInfoRepository.save(entity);
        return MqttBrokerInfoDto.from(savedEntity);
    }

    @Transactional
    public MqttBrokerInfoDto updateByBuilding(Long buildingId, MqttBrokerCreateRequest request) {
        MqttBrokerInfo broker = mqttBrokerInfoRepository.findFirstByBuildingId(buildingId);
        broker.update(request.serverName(), request.brokerUrl(), request.username(), request.password(), request.topic());
        return MqttBrokerInfoDto.from(broker);
    }

    @Transactional(readOnly = true)
    public Optional<MqttBrokerInfoDto> getBrokerByBuildingId(Long buildingId) {
        return mqttBrokerInfoRepository.findAllByBuildingId(buildingId).stream()
                .findFirst()
                .map(MqttBrokerInfoDto::from);
    }

    @Transactional
    public void delete(Long id) {
        sensorMeasurementRepository.deleteAllByBrokerId(id);
        sensorDeviceRepository.deleteAllByBrokerId(id);
        mqttBrokerInfoRepository.findById(id)
                .ifPresent(mqttBrokerInfoRepository::delete);
    }

    @Transactional
    public List<Long> deleteByBuildingId(Long buildingId) {
        List<MqttBrokerInfo> brokers = mqttBrokerInfoRepository.findAllByBuildingId(buildingId);
        if (brokers.isEmpty()) {
            return List.of();
        }
        List<Long> brokerIds = brokers.stream().map(MqttBrokerInfo::getId).toList();

        sensorMeasurementRepository.deleteAllByBuildingId(buildingId);
        sensorDeviceRepository.deleteAllByBuildingId(buildingId);
        mqttBrokerInfoRepository.deleteAll(brokers);

        return brokerIds;
    }
}
