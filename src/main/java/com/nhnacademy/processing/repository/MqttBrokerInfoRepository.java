package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MqttBrokerInfoRepository extends JpaRepository<MqttBrokerInfo, Long> {
    List<MqttBrokerInfo> findAllByEnabled(Boolean enabled);

    List<MqttBrokerInfo> findAllByBuildingId(Long buildingId);

    Optional<MqttBrokerInfo> findFirstByBuildingId(Long buildingId);
}
