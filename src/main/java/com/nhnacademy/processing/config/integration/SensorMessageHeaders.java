package com.nhnacademy.processing.config.integration;

/**
 * 센서 처리 파이프라인 전반에서 사용하는 Message Header 키 상수.
 * MqttBrokerRegistry에서 세팅되고, 파이프라인 각 단계에서 참조된다.
 */
public final class SensorMessageHeaders {

    private SensorMessageHeaders() {}

    /** 메시지를 수신한 MQTT 브로커 ID (SensorDeviceRegistry.ensureRegistered 등에서 사용) */
    public static final String BROKER_ID = "brokerId";

    /** SensorContextResolver로 조회한 roomId. 조회 실패 시 -1 */
    public static final String ROOM_ID = "roomId";

    /** Splitter로 쪼개지기 전의 원본 ParsedSensorMessage. */
    public static final String PARSED_MESSAGE = "parsedMessage";
}
