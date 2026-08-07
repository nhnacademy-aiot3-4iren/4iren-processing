package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.service.mqtt.MqttBrokerRegistry;
import com.nhnacademy.processing.service.mqtt.MqttBrokerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/processing/mqtt")
@RequiredArgsConstructor
public class MqttBrokerController {

    private final MqttBrokerService mqttBrokerService;
    private final MqttBrokerRegistry mqttBrokerRegistry;

    @PostMapping
    public ResponseEntity<MqttBrokerInfoDto> registerBroker(@RequestBody MqttBrokerCreateRequest request) {
        MqttBrokerInfoDto broker = mqttBrokerService.register(request);
        mqttBrokerRegistry.registerBroker(broker);
        return ResponseEntity.status(HttpStatus.CREATED).body(broker);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBroker(@PathVariable Long id) {
        mqttBrokerRegistry.unregisterBroker(id);
        mqttBrokerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
