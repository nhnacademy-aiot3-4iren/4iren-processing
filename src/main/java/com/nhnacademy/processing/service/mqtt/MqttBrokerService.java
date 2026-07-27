package com.nhnacademy.processing.service.mqtt;

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
}
