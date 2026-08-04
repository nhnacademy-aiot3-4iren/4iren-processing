package com.nhnacademy.processing.service.mqtt;

import com.nhnacademy.processing.integration.SensorErrorFlowConfig;
import com.nhnacademy.processing.integration.SensorMessageHeaders;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
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
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * MQTT 브로커별 동적 어댑터 등록/해제
 * sensorInputChannel로 메시지를 넘김
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttBrokerRegistry {

    private final IntegrationFlowContext flowContext;
    private final MqttBrokerService mqttBrokerService;
    private final ExecutorService mqttProcessingExecutor;

    private final Map<Long, IntegrationFlowContext.IntegrationFlowRegistration> registrations = new ConcurrentHashMap<>();

    private final MessageChannel sensorInputChannel;
    private final MessageChannel sensorErrorChannel;

    @PostConstruct
    public void init() {
        mqttBrokerService.getMqttBrokerInfo().forEach(this::registerBroker);
        log.info("MQTT 브로커 {}개 등록 완료", registrations.size());
    }

    public void registerBroker(MqttBrokerInfoDto info) {
        Long brokerId = info.id();

        try {
            MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter("4iren-"+ (brokerId+1), createClientFactory(info), info.topic());
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
                Message<?> withBrokerId = MessageBuilder.fromMessage(message)
                        .setHeader(SensorMessageHeaders.BROKER_ID, brokerId)
                        .build();
                sensorInputChannel.send(withBrokerId);
            } catch (Exception e) {
                sensorErrorChannel.send(MessageBuilder.withPayload(new SensorErrorFlowConfig.ProcessingFailure(brokerId, e)).build());
            } finally {
                if (ackCallBack instanceof SimpleAcknowledgment ack) {
                    ack.acknowledge();
                }
            }
        });
    }

    private MqttPahoClientFactory createClientFactory(MqttBrokerInfoDto info) {
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
