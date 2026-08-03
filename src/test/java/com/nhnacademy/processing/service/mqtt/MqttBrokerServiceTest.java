package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttBrokerServiceTest {

    @Mock
    private MqttBrokerInfoRepository repository;

    @InjectMocks
    private MqttBrokerService service;

    @Test
    @DisplayName("활성화된 브로커 목록을 반환")
    void testGetMqttBrokerInfo() {
        List<MqttBrokerInfo> infoList = List.of(mock(MqttBrokerInfo.class), mock(MqttBrokerInfo.class));
        when(repository.findAllByEnabled(true)).thenReturn(infoList);

        List<MqttBrokerInfoDto> dtoList = service.getMqttBrokerInfo();

        assertThat(dtoList).hasSize(2);
        assertThat(dtoList.getFirst()).isInstanceOf(MqttBrokerInfoDto.class);
    }
}
