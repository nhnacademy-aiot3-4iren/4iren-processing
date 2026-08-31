package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MqttBrokerService {

    private final MqttBrokerInfoRepository mqttBrokerInfoRepository;

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
    public void delete(Long id) {
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
        mqttBrokerInfoRepository.deleteAll(brokers);
        return brokerIds;
    }
}
