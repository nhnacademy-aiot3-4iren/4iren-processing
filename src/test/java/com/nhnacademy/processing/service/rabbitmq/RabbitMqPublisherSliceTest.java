package com.nhnacademy.processing.service.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.processing.config.RabbitMQConfig;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = {RabbitMQConfig.class, RabbitMqPublisher.class},
        properties = {
                "spring.rabbitmq.template.exchange=sensor.topic.exchange.test",
                "spring.rabbitmq.template.routing-key=sensor.env.test"
        }
)
@ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
class RabbitMqPublisherSliceTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private RabbitMqPublisher publisher;
    @Autowired
    private RabbitTemplate template;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.rabbitmq.template.exchange}")
    private String exchange;
    @Value("${spring.rabbitmq.template.routing-key}")
    private String routingKey;

    private String testQueueName;

    @BeforeEach
    void setUp() {
        testQueueName = "test-queue-" + UUID.randomUUID();
        Queue queue = new Queue(testQueueName, false, false, true);
        Binding binding = BindingBuilder.bind(queue)
                .to(new TopicExchange(exchange))
                .with(routingKey);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);
    }

    @Test
    @DisplayName("발행한 메시지가 지정된 Exchange와 RoutingKey로 정확히 전달됨")
    void messageRouting_Success() throws Exception {
        // given
        String devEui = "1234567890abcdef";
        ParsedSensorMessage message = sampleMessage(devEui);

        // when
        publisher.publish(message);

        // then
        // 1. 순수 Message 객체로 수신 (AMQP Trusted Packages 검증 우회)
        Message receivedMessage = template.receive(testQueueName, 5000);
        assertThat(receivedMessage).isNotNull();

        // 2. ObjectMapper를 이용해 직접 바이트 배열을 파싱
        ParsedSensorMessage received = objectMapper.readValue(receivedMessage.getBody(), ParsedSensorMessage.class);

        assertThat(received.device().devEui()).isEqualTo(devEui);
        assertThat(received.sensorDataList()).hasSize(2);
        assertThat(received.sensorDataList().getFirst().measurement()).isEqualTo("temperature");
    }

    @Test
    @DisplayName("다른 RoutingKey로 바인딩된 큐에는 메시지가 전달되지 않는다")
    void publish_IgnoredByMismatchedRoutingKey() {
        // given
        String mismatchedQueue = "test-queue-mismatch-" + UUID.randomUUID();
        Queue queue = new Queue(mismatchedQueue, false, false, true);
        Binding binding = BindingBuilder.bind(queue)
                .to(new TopicExchange(exchange))
                .with("sensor.other.mismatch");
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        // when
        publisher.publish(sampleMessage("devEuiPubTest2"));

        // then: receiveAndConvert 대신 순수 receive 사용
        Message receivedMessage = template.receive(mismatchedQueue, 2000);
        assertThat(receivedMessage).isNull();
    }

    @Test
    @DisplayName("발행된 메시지의 모든 중첩 필드와 날짜(Instant)가 손실 없이 직렬화/역직렬화된다")
    void publish_SerializeAndDeserializeWithoutLoss() throws Exception {
        // given
        Instant now = Instant.now();
        ParsedSensorMessage message = new ParsedSensorMessage(
                new DeviceIdentity("app1", "appName1", "profile1", "device3", "devEuiPubTest3", "사무실", 101),
                List.of(
                        new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 24.5),
                        new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 80.0)
                ),
                now
        );

        // when
        publisher.publish(message);

        // then
        Message receivedMessage = template.receive(testQueueName, 5000);
        assertThat(receivedMessage).isNotNull();

        ParsedSensorMessage received = objectMapper.readValue(receivedMessage.getBody(), ParsedSensorMessage.class);

        assertThat(received.measuredAt()).isEqualTo(now);
        assertThat(received.device().roomId()).isEqualTo(101);
        assertThat(received.sensorDataList())
                .extracting(SensorData::measurement)
                .containsExactly("temperature", "battery");
    }

    private ParsedSensorMessage sampleMessage(String devEui) {
        return new ParsedSensorMessage(
                new DeviceIdentity("applicationId", "applicationName",
                        "deviceProfileId", "deviceName", devEui,
                        null, 101
                ),
                List.of(
                        new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0),
                        new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 900.0)
                ),
                Instant.now()
        );
    }
}