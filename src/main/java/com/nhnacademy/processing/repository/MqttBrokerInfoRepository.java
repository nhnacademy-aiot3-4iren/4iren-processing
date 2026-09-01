package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MqttBrokerInfoRepository extends JpaRepository<MqttBrokerInfo, Long> {
    List<MqttBrokerInfo> findAllByEnabled(Boolean enabled);

    List<MqttBrokerInfo> findAllByBuildingId(Long buildingId);
}
