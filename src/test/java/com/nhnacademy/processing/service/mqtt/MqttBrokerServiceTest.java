package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("새로운 브로커 정보를 등록하고 DTO를 반환한다")
    void testRegisterMqttBroker() {
        // Given
        MqttBrokerCreateRequest request = new MqttBrokerCreateRequest(
                "NHN Academy Broker",
                "tcp://localhost:1883",
                "user",
                "pass",
                "application/+/device/+/event/up"
        );

        // repository.save() 호출 시 반환될 Mock Entity 설정 (ID가 부여된 상태로 가정)
        MqttBrokerInfo savedEntity = new MqttBrokerInfo(
                1L,
                request.serverName(),
                request.brokerUrl(),
                request.username(),
                request.password(),
                request.topic()
        );

        when(repository.save(any(MqttBrokerInfo.class))).thenReturn(savedEntity);

        // When
        MqttBrokerInfoDto result = service.register(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.serverName()).isEqualTo("NHN Academy Broker");
        assertThat(result.brokerUrl()).isEqualTo("tcp://localhost:1883");
        assertThat(result.username()).isEqualTo("user");
        assertThat(result.topic()).isEqualTo("application/+/device/+/event/up");

        // DB save 메서드가 정확히 1번 호출되었는지 검증
        verify(repository, times(1)).save(any(MqttBrokerInfo.class));
    }

    @Test
    @DisplayName("특정 ID의 브로커 정보 삭제")
    void testDeleteMqttBroker() {
        Long brokerId = 1L;
        doNothing().when(repository).deleteById(brokerId);

        service.delete(brokerId);

        verify(repository, times(1)).deleteById(brokerId);
    }
}
