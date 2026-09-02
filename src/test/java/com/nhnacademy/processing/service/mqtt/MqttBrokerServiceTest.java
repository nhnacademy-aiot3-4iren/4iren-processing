package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerUpdateRequest;
import com.nhnacademy.processing.exception.MqttBrokerNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        when(repository.existsByBuildingId(buildingId)).thenReturn(false);
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

        verify(repository, times(1)).existsByBuildingId(buildingId);
        verify(repository, times(1)).save(any(MqttBrokerInfo.class));
    }

    @Test
    @DisplayName("이미 브로커가 등록된 건물이면 등록 시 IllegalArgumentException 발생")
    void testRegisterMqttBroker_DuplicateBuilding_ThrowsException() {
        // Given
        MqttBrokerCreateRequest request = new MqttBrokerCreateRequest(
                buildingId,
                "Duplicate Broker",
                "tcp://localhost:1883",
                "user",
                "pass",
                "topic"
        );

        when(repository.existsByBuildingId(buildingId)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(buildingId));

        verify(repository, times(1)).existsByBuildingId(buildingId);
        verify(repository, never()).save(any(MqttBrokerInfo.class));
    }

    @Test
    @DisplayName("건물 ID 기준 브로커 정보 수정 성공")
    void testUpdateByBuilding_Success() {
        // Given
        MqttBrokerUpdateRequest updateRequest = new MqttBrokerUpdateRequest(
                "Updated Broker",
                "tcp://newhost:1883",
                "newUser",
                "newPass",
                "new/topic"
        );

        MqttBrokerInfo existingBroker = new MqttBrokerInfo(
                1L,
                buildingId,
                "Old Broker",
                "tcp://oldhost:1883",
                "oldUser",
                "oldPass",
                "old/topic"
        );

        when(repository.findByBuildingId(buildingId)).thenReturn(Optional.of(existingBroker));

        // When
        MqttBrokerInfoDto result = service.updateByBuilding(buildingId, updateRequest);

        // Then
        assertThat(result.serverName()).isEqualTo("Updated Broker");
        assertThat(result.brokerUrl()).isEqualTo("tcp://newhost:1883");
        assertThat(result.username()).isEqualTo("newUser");
        assertThat(result.password()).isEqualTo("newPass");
        assertThat(result.topic()).isEqualTo("new/topic");
        verify(repository, times(1)).findByBuildingId(buildingId);
    }

    @Test
    @DisplayName("존재하지 않는 건물의 브로커 수정 시 MqttBrokerNotFoundException 발생")
    void testUpdateByBuilding_NotFound_ThrowsException() {
        // Given
        MqttBrokerUpdateRequest updateRequest = new MqttBrokerUpdateRequest(
                "Updated Broker",
                "tcp://newhost:1883",
                "newUser",
                "newPass",
                "new/topic"
        );

        when(repository.findByBuildingId(buildingId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.updateByBuilding(buildingId, updateRequest))
                .isInstanceOf(MqttBrokerNotFoundException.class);

        verify(repository, times(1)).findByBuildingId(buildingId);
    }

    @Test
    @DisplayName("건물 ID 기준 브로커 단건 조회")
    void testGetBrokerByBuildingId() {
        // Given
        MqttBrokerInfo broker = new MqttBrokerInfo(
                1L,
                buildingId,
                "NHN Broker",
                "tcp://localhost:1883",
                "user",
                "pass",
                "topic"
        );
        when(repository.findByBuildingId(buildingId)).thenReturn(Optional.of(broker));

        // When
        Optional<MqttBrokerInfoDto> result = service.getBrokerByBuildingId(buildingId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(1L);
        assertThat(result.get().buildingId()).isEqualTo(buildingId);
        verify(repository, times(1)).findByBuildingId(buildingId);
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
    @DisplayName("건물 ID 기준 브로커 삭제 - 연관 데이터 벌크 삭제 및 삭제된 브로커 ID Optional 반환")
    void testDeleteByBuildingId() {
        // Given
        MqttBrokerInfo broker = mock(MqttBrokerInfo.class);
        when(broker.getId()).thenReturn(1L);
        when(repository.findByBuildingId(buildingId)).thenReturn(Optional.of(broker));

        // When
        Optional<Long> deletedId = service.deleteByBuildingId(buildingId);

        // Then
        assertThat(deletedId).contains(1L);
        verify(sensorMeasurementRepository, times(1)).deleteAllByBuildingId(buildingId);
        verify(sensorDeviceRepository, times(1)).deleteAllByBuildingId(buildingId);
        verify(repository, times(1)).delete(broker);
    }

    @Test
    @DisplayName("건물 ID 기준 브로커 삭제 - 브로커가 없으면 연관 데이터 삭제 없이 Optional.empty 반환")
    void testDeleteByBuildingId_NotFound() {
        // Given
        when(repository.findByBuildingId(buildingId)).thenReturn(Optional.empty());

        // When
        Optional<Long> deletedId = service.deleteByBuildingId(buildingId);

        // Then
        assertThat(deletedId).isEmpty();
        verify(sensorMeasurementRepository, never()).deleteAllByBuildingId(any());
        verify(sensorDeviceRepository, never()).deleteAllByBuildingId(any());
        verify(repository, never()).delete(any(MqttBrokerInfo.class));
    }
}