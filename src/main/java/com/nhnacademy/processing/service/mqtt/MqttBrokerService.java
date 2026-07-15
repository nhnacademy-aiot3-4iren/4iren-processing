package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MqttBrokerService {

    private final MqttBrokerInfoRepository mqttBrokerInfoRepository;

    public List<MqttBrokerInfo> getMqttBrokerInfo() {
        return new ArrayList<>(mqttBrokerInfoRepository.findAllByEnabled(true));
    }
}
