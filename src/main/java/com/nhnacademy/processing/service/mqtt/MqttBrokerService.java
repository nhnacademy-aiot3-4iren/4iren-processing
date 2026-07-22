package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.dto.mqtt.MqttBrokerDto;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MqttBrokerService {

    private final MqttBrokerInfoRepository mqttBrokerInfoRepository;

    public List<MqttBrokerDto> getMqttBrokerInfo() {
        return mqttBrokerInfoRepository.findAllByEnabled(true).stream()
                .map(MqttBrokerDto::from)
                .toList();
    }
}
