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

    @MockitoBean private SensorPayloadConverter payloadConverter;
    @MockitoBean private SensorContextResolver contextResolver;
    @MockitoBean private SensorDeviceRegistry sensorDeviceRegistry;
    @MockitoBean private SensorValidator sensorValidator;

    @MockitoBean private InfluxDbWriter influxDbWriter;
    @MockitoBean private SensorAnomalyLogService anomalyLogService;
    @MockitoBean private RabbitTemplate rabbitTemplate;

    private DeviceIdentity mockDevice;
    private static final String RAW_PAYLOAD = "raw-mqtt-payload";
    private static final Long BROKER_ID = 1L;
    private static final int VALID_ROOM_ID = 101;

    @BeforeEach
    void setUp() {
        mockDevice = new DeviceIdentity("app1", "appName", "prof1", "deviceName", "devEui123", "loc", VALID_ROOM_ID);

        when(contextResolver.resolve("devEui123")).thenReturn(Optional.of(new SensorContext("devEui123", VALID_ROOM_ID, 1)));
        doNothing().when(sensorDeviceRegistry).ensureRegistered(any(), any(), anyInt());

        MessageConverter mockConverter = mock(MessageConverter.class);
        when(mockConverter.toMessage(any(), any(MessageProperties.class)))
                .thenReturn(new Message("dummy".getBytes()));

        when(rabbitTemplate.getMessageConverter()).thenReturn(mockConverter);
    }

    @Test
    @DisplayName("정상 환경 데이터는 InfluxDB에 저장, RabbitMQ로 발행")
    void normalData_RoutesTo_InfluxAndRabbitMq() {
        SensorData normalEnvData = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0);
        ParsedSensorMessage parsedMessage = new ParsedSensorMessage(mockDevice, List.of(normalEnvData), Instant.now());

        when(payloadConverter.convert(RAW_PAYLOAD)).thenReturn(parsedMessage);
        when(sensorValidator.validate(normalEnvData)).thenReturn(ValidationStatus.VALID);

        sensorInputChannel.send(MessageBuilder.withPayload(RAW_PAYLOAD)
                .setHeader(SensorMessageHeaders.BROKER_ID, BROKER_ID)
                .build());

        verify(influxDbWriter, times(1)).writeAsync(eq(normalEnvData), any(), eq(VALID_ROOM_ID));
        verify(anomalyLogService, never()).log(any(), any(), anyInt(), any(), any());
        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class), any());
    }

    @Test
    @DisplayName("이상 환경 데이터는 ES 로그")
    void anomalyData_RoutesTo_ES_And_DroppedFromMQ() {
        SensorData anomalyEnvData = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 999.0);
        ParsedSensorMessage parsedMessage = new ParsedSensorMessage(mockDevice, List.of(anomalyEnvData), Instant.now());

        when(payloadConverter.convert(RAW_PAYLOAD)).thenReturn(parsedMessage);
        when(sensorValidator.validate(anomalyEnvData)).thenReturn(ValidationStatus.OUT_OF_RANGE); // 이상 데이터 판정

        sensorInputChannel.send(MessageBuilder.withPayload(RAW_PAYLOAD)
                .setHeader(SensorMessageHeaders.BROKER_ID, BROKER_ID)
                .build());

        verify(anomalyLogService, times(1)).log(eq(anomalyEnvData), eq("devEui123"), eq(VALID_ROOM_ID), eq(ValidationStatus.OUT_OF_RANGE), any());
        verify(influxDbWriter, never()).writeAsync(any(), any(), anyInt());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class), any());
    }

    @Test
    @DisplayName("RoomId가 없는 기기의 데이터는 DB에는 저장, MQ로는 발행X")
    void unknownRoomId_RoutesTo_DB_But_DroppedFromMQ() {
        when(contextResolver.resolve("devEui123")).thenReturn(Optional.empty());

        SensorData healthData = new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 100.0);
        ParsedSensorMessage parsedMessage = new ParsedSensorMessage(mockDevice, List.of(healthData), Instant.now());

        when(payloadConverter.convert(RAW_PAYLOAD)).thenReturn(parsedMessage);

        sensorInputChannel.send(MessageBuilder.withPayload(RAW_PAYLOAD)
                .setHeader(SensorMessageHeaders.BROKER_ID, BROKER_ID)
                .build());

        verify(influxDbWriter, times(1)).writeAsync(eq(healthData), any(), eq(-1));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class), any());
    }
}