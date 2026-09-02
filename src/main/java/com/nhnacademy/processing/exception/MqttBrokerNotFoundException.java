package com.nhnacademy.processing.exception;

public class MqttBrokerNotFoundException extends RuntimeException {

    public MqttBrokerNotFoundException(Long buildingId) {
        super("건물 ID에 해당하는 MQTT 브로커를 찾을 수 없습니다: " + buildingId);
    }
}