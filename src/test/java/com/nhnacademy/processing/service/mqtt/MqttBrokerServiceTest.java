package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.repository.MqttBrokerInfoRepository;
import com.nhnacademy.processing.repository.SensorDeviceRepository;
import com.nhnacademy.processing.repository.SensorMeasurementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttBrokerServiceTest {

    @Mock
    private MqttBrokerInfoRepository repository;

    @Mock
    private SensorMeasurementRepository sensorMeasurementRepository;

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @InjectMocks
    private MqttBrokerService service;

    private final Long buildingId = 101L;

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
                buildingId,
                "NHN Academy Broker",
                "tcp://localhost:1883",
                "user",
                "pass",
                "application/+/device/+/event/up"
        );

        MqttBrokerInfo savedEntity = new MqttBrokerInfo(
                1L,
                buildingId,
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
        assertThat(result.buildingId()).isEqualTo(buildingId);
        assertThat(result.serverName()).isEqualTo("NHN Academy Broker");
        assertThat(result.brokerUrl()).isEqualTo("tcp://localhost:1883");
        assertThat(result.username()).isEqualTo("user");
        assertThat(result.topic()).isEqualTo("application/+/device/+/event/up");

        verify(repository, times(1)).save(any(MqttBrokerInfo.class));
    }

    @Test
    @DisplayName("특정 ID의 브로커 정보 삭제 - 존재하는 경우 연관 데이터 벌크 삭제 후 브로커 삭제")
    void testDeleteMqttBroker() {
        // Given
        Long brokerId = 1L;
        MqttBrokerInfo brokerInfo = mock(MqttBrokerInfo.class);
        when(repository.findById(brokerId)).thenReturn(Optional.of(brokerInfo));

        // When
        service.delete(brokerId);

        // Then
        verify(sensorMeasurementRepository, times(1)).deleteAllByBrokerId(brokerId);
        verify(sensorDeviceRepository, times(1)).deleteAllByBrokerId(brokerId);
        verify(repository, times(1)).findById(brokerId);
        verify(repository, times(1)).delete(brokerInfo);
    }

    @Test
    @DisplayName("특정 ID의 브로커 정보 삭제 - 브로커가 없어도 연관 데이터 정리 후 안전하게 종료")
    void testDeleteMqttBroker_NotFound() {
        // Given
        Long brokerId = 1L;
        when(repository.findById(brokerId)).thenReturn(Optional.empty());

        // When
        service.delete(brokerId);

        // Then
        verify(sensorMeasurementRepository, times(1)).deleteAllByBrokerId(brokerId);
        verify(sensorDeviceRepository, times(1)).deleteAllByBrokerId(brokerId);
        verify(repository, times(1)).findById(brokerId);
        verify(repository, never()).delete(any(MqttBrokerInfo.class));
    }

    @Test
    @DisplayName("건물 ID 기준 브로커 삭제 - 연관 데이터 벌크 삭제 및 삭제된 브로커 ID 목록 반환")
    void testDeleteByBuildingId() {
        // Given
        MqttBrokerInfo broker1 = mock(MqttBrokerInfo.class);
        MqttBrokerInfo broker2 = mock(MqttBrokerInfo.class);
        when(broker1.getId()).thenReturn(1L);
        when(broker2.getId()).thenReturn(2L);

        List<MqttBrokerInfo> brokers = List.of(broker1, broker2);
        when(repository.findAllByBuildingId(buildingId)).thenReturn(brokers);

        // When
        List<Long> deletedIds = service.deleteByBuildingId(buildingId);

        // Then
        assertThat(deletedIds).containsExactly(1L, 2L);
        verify(sensorMeasurementRepository, times(1)).deleteAllByBuildingId(buildingId);
        verify(sensorDeviceRepository, times(1)).deleteAllByBuildingId(buildingId);
        verify(repository, times(1)).deleteAll(brokers);
    }
}