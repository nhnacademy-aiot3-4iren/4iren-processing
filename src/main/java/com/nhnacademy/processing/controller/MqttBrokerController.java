package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.auth.AuthUser;
import com.nhnacademy.processing.auth.LoginUser;
import com.nhnacademy.processing.auth.RequireAdmin;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerCreateRequest;
import com.nhnacademy.processing.dto.mqtt.MqttBrokerInfoDto;
import com.nhnacademy.processing.service.mqtt.MqttBrokerRegistry;
import com.nhnacademy.processing.service.mqtt.MqttBrokerService;
import jakarta.validation.Valid;
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

    @RequireAdmin
    @PostMapping
    public ResponseEntity<MqttBrokerInfoDto> registerBroker(@LoginUser AuthUser authUser,
                                                            @Valid @RequestBody MqttBrokerCreateRequest request) {
        MqttBrokerInfoDto broker = mqttBrokerService.register(request);
        try {
            mqttBrokerRegistry.registerBroker(broker);
        } catch (Exception e) {
            mqttBrokerService.delete(broker.id());
            throw e;
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(broker);
    }

    @RequireAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBroker(@LoginUser AuthUser authUser,
                                             @PathVariable Long id) {
        mqttBrokerRegistry.unregisterBroker(id);
        mqttBrokerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
