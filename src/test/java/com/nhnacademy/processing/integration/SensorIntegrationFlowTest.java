package com.nhnacademy.processing.integration;

import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.service.converter.SensorPayloadConverter;
import com.nhnacademy.processing.service.es.SensorAnomalyLogService;
import com.nhnacademy.processing.service.influx.InfluxDbWriter;
import com.nhnacademy.processing.service.process.SensorContextResolver;
import com.nhnacademy.processing.service.sensor.SensorDeviceRegistry;
import com.nhnacademy.processing.service.validation.SensorValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {
        SensorIntegrationChannelConfig.class,
        SensorIntegrationFlowConfig.class,
        SensorDbSubFlowConfig.class,
        SensorMqSubFlowConfig.class,
        SensorErrorFlowConfig.class
})
@EnableIntegration
@TestPropertySource(properties = {
        "spring.rabbitmq.template.exchange=test.exchange",
        "spring.rabbitmq.template.routing-key=test.routing.key"
})
class SensorIntegrationFlowTest {

    @Autowired
    @Qualifier("sensorInputChannel")
    private MessageChannel sensorInputChannel;

    // 💡 3. 흐름에 필요한 외부 서비스들은 모두 가짜(Mock) 객체로 만들어 Flow만 검증합니다.
    @MockitoBean private SensorPayloadConverter payloadConverter;
    @MockitoBean private SensorContextResolver contextResolver;
    @MockitoBean private SensorDeviceRegistry sensorDeviceRegistry;
    @MockitoBean private SensorValidator sensorValidator;

    @MockitoBean private InfluxDbWriter influxDbWriter;
    @MockitoBean private SensorAnomalyLogService anomalyLogService;
    @MockitoBean private RabbitTemplate rabbitTemplate;

    private DeviceIdentity mockDevice;
    private static final String RAW_PAYLOAD = "dummy-raw-mqtt-payload";
    private static final Long BROKER_ID = 1L;
    private static final int VALID_ROOM_ID = 101;

    @BeforeEach
    void setUp() {
        mockDevice = new DeviceIdentity("app1", "appName", "prof1", "deviceName", "devEui123", "loc", VALID_ROOM_ID);

        // 공통 전처리 Mocking (Header Enricher, Service Activator 무사 통과용)
        when(contextResolver.resolve("devEui123")).thenReturn(Optional.of(new SensorContext("devEui123", VALID_ROOM_ID, 1)));
        doNothing().when(sensorDeviceRegistry).ensureRegistered(any(), any(), anyInt());

        // 💡 추가된 부분: Amqp.outboundAdapter가 사용할 가짜 MessageConverter 세팅
        MessageConverter mockConverter = mock(MessageConverter.class);
        when(mockConverter.toMessage(any(), any(MessageProperties.class)))
                .thenReturn(new Message("dummy".getBytes())); // 변환 결과로 아무 더미 메시지나 반환

        when(rabbitTemplate.getMessageConverter()).thenReturn(mockConverter);
    }

    @Test
    @DisplayName("정상 환경 데이터는 InfluxDB에 저장되고 RabbitMQ로 발행된다 (이상 로그는 쌓이지 않음)")
    void normalData_RoutesTo_InfluxAndRabbitMq() {
        // given
        SensorData normalEnvData = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0);
        ParsedSensorMessage parsedMessage = new ParsedSensorMessage(mockDevice, List.of(normalEnvData), Instant.now());

        when(payloadConverter.convert(RAW_PAYLOAD)).thenReturn(parsedMessage);
        when(sensorValidator.validate(normalEnvData)).thenReturn(ValidationStatus.VALID); // 정상 데이터 판정

        // when
        sensorInputChannel.send(MessageBuilder.withPayload(RAW_PAYLOAD)
                .setHeader(SensorMessageHeaders.BROKER_ID, BROKER_ID)
                .build());

        // then: DB 분기 검증
        verify(influxDbWriter, times(1)).writeAsync(eq(normalEnvData), any(), eq(VALID_ROOM_ID));
        verify(anomalyLogService, never()).log(any(), any(), anyInt(), any(), any());

        // then: MQ 분기 검증
        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class), any());
    }

    @Test
    @DisplayName("이상 환경 데이터는 ES 로그로 빠지고, 유효한 데이터가 없으므로 MQ로는 발행되지 않는다")
    void anomalyData_RoutesTo_ES_And_DroppedFromMQ() {
        // given
        SensorData anomalyEnvData = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 999.0);
        ParsedSensorMessage parsedMessage = new ParsedSensorMessage(mockDevice, List.of(anomalyEnvData), Instant.now());

        when(payloadConverter.convert(RAW_PAYLOAD)).thenReturn(parsedMessage);
        when(sensorValidator.validate(anomalyEnvData)).thenReturn(ValidationStatus.OUT_OF_RANGE); // 이상 데이터 판정

        // when
        sensorInputChannel.send(MessageBuilder.withPayload(RAW_PAYLOAD)
                .setHeader(SensorMessageHeaders.BROKER_ID, BROKER_ID)
                .build());

        // then: DB 분기 검증
        verify(anomalyLogService, times(1)).log(eq(anomalyEnvData), eq("devEui123"), eq(VALID_ROOM_ID), eq(ValidationStatus.OUT_OF_RANGE), any());
        verify(influxDbWriter, never()).writeAsync(any(), any(), anyInt());

        // then: MQ 분기 검증 (필터에서 드랍되어 RabbitTemplate이 호출되지 않아야 함)
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class), any());
    }

    @Test
    @DisplayName("RoomId가 없는(-1) 기기의 데이터는 DB에는 저장되지만 MQ로는 발행되지 않는다")
    void unknownRoomId_RoutesTo_DB_But_DroppedFromMQ() {
        // given
        when(contextResolver.resolve("devEui123")).thenReturn(Optional.empty()); // 캐시(API) 조회 실패 가정 -> RoomId -1 반환됨

        SensorData healthData = new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 100.0);
        ParsedSensorMessage parsedMessage = new ParsedSensorMessage(mockDevice, List.of(healthData), Instant.now());

        when(payloadConverter.convert(RAW_PAYLOAD)).thenReturn(parsedMessage);

        // when
        sensorInputChannel.send(MessageBuilder.withPayload(RAW_PAYLOAD)
                .setHeader(SensorMessageHeaders.BROKER_ID, BROKER_ID)
                .build());

        // then: DB 분기 검증 (디바이스 헬스 데이터는 정상으로 분류되어 InfluxDB 저장)
        verify(influxDbWriter, times(1)).writeAsync(eq(healthData), any(), eq(-1));

        // then: MQ 분기 검증 (RoomId 필터에서 드랍되어 발행 안됨)
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class), any());
    }
}