package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.dto.mqtt.MqttBrokerDto;
import com.nhnacademy.processing.dto.sensor.ParsedSensorMessage;
import com.nhnacademy.processing.service.process.SensorPayloadConverter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.SimpleAcknowledgment;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttBrokerRegistry {

    private final IntegrationFlowContext flowContext;
    private final MqttBrokerService mqttBrokerService;
    private final ExecutorService mqttProcessingExecutor;

    private final Map<Long, IntegrationFlowContext.IntegrationFlowRegistration> registrations = new ConcurrentHashMap<>();
    private final SensorPayloadConverter sensorPayloadConverter;

    @PostConstruct
    public void init() {
        mqttBrokerService.getMqttBrokerInfo().forEach(this::registerBroker);
        log.info("MQTT 브로커 {}개 등록 완료", registrations.size());
    }

    public void registerBroker(MqttBrokerDto info) {
        Long brokerId = info.id();

        try {
            MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter("4iren-"+brokerId, createClientFactory(info), info.topic());
            adapter.setQos(1);
            adapter.setManualAcks(true);
            adapter.setCompletionTimeout(10000);
            adapter.setConverter(new DefaultPahoMessageConverter());
            adapter.setOutputChannel(new DirectChannel());

            IntegrationFlow flow = IntegrationFlow.from(adapter)
                    .handle(message -> handleMessage(brokerId, message))
                    .get();

            IntegrationFlowContext.IntegrationFlowRegistration registration = flowContext.registration(flow)
                    .id("mqtt-flow-"+brokerId)
                    .register();

            registrations.put(brokerId, registration);
            log.info("브로커 등록됨: brokerId({}), url({})", brokerId, info.brokerUrl());
        } catch (Exception e) {
            log.error("브로커 등록 실패: brokerId({}), url({})", brokerId, info.brokerUrl(), e);
        }
    }

    public void unregisterBroker(Long brokerId) {
        IntegrationFlowContext.IntegrationFlowRegistration registration = registrations.remove(brokerId);
        if(registration != null) {
            registration.destroy();
            log.info("브로커 해제됨: brokerId({})", brokerId);
        }
    }

    private void handleMessage(Long brokerId, Message<?> message) {
        IntegrationMessageHeaderAccessor accessor = new IntegrationMessageHeaderAccessor(message);
        Object ackCallBack = accessor.getHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK);

        mqttProcessingExecutor.submit(() -> {
            try {
                ParsedSensorMessage parsedMessage = sensorPayloadConverter.convert(message.getPayload().toString());
                log.info(parsedMessage.toString());
                if (ackCallBack instanceof SimpleAcknowledgment ack) {
                   ack.acknowledge();
                }
            } catch (Exception e) {
                log.error("메시지 처리 실패: brokerId({})", brokerId, e);
            }
        });
    }

    private MqttPahoClientFactory createClientFactory(MqttBrokerDto info) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{info.brokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        if (info.username() != null) {
            options.setUserName(info.username());
            options.setPassword(info.password().toCharArray());
        }

        factory.setConnectionOptions(options);

        return factory;
    }
}
