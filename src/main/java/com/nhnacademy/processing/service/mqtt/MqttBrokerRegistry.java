package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.domain.MqttBrokerInfo;
import com.nhnacademy.processing.dto.RawSensorMessage;
import com.nhnacademy.processing.service.process.SensorMessageHandler;
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
import org.springframework.integration.mqtt.support.MqttHeaders;
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
//    private final SensorMessageHandler sensorMessageHandler;
    private final ExecutorService mqttProcessingExecutor;

    private final Map<Long, IntegrationFlowContext.IntegrationFlowRegistration> registrations = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        mqttBrokerService.getMqttBrokerInfo().forEach(this::registerBroker);
        log.info("MQTT 브로커 {}개 등록 완료", registrations.size());
    }

    public void registerBroker(MqttBrokerInfo info) {
        Long brokerId = info.getId();

        try {
            MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter("4iren-"+brokerId, createClientFactory(info), info.getTopic());
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
            log.info("브로커 등록됨: brokerId({}), url({})", brokerId, info.getBrokerUrl());
        } catch (Exception e) {
            log.error("브로커 등록 실패: brokerId({}), url({})", brokerId, info.getBrokerUrl(), e);
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
        log.info(message.toString());

        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String rawPayload = (String) message.getPayload();

        IntegrationMessageHeaderAccessor accessor = new IntegrationMessageHeaderAccessor(message);
        Object ackCallBack = accessor.getHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK);

        mqttProcessingExecutor.submit(() -> {
            try {
//                sensorMessageHandler.handle(new RawSensorMessage(brokerId, topic, rawPayload));
                if (ackCallBack instanceof SimpleAcknowledgment ack) {
                   ack.acknowledge();
                }
            } catch (Exception e) {
                log.error("메시지 처리 실패: brokerId({}), topic({})", brokerId, topic, e);
            }
        });
    }

    private MqttPahoClientFactory createClientFactory(MqttBrokerInfo info) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{info.getBrokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        if (info.getUsername() != null) {
            options.setUserName(info.getUsername());
            options.setPassword(info.getPassword().toCharArray());
        }

        factory.setConnectionOptions(options);

        return factory;
    }
}
