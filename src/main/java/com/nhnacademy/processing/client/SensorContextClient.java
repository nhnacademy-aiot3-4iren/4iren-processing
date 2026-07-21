package com.nhnacademy.processing.client;

import com.nhnacademy.processing.dto.sensor.SensorContext;
import com.nhnacademy.processing.exception.InvalidDevEuiException;
import com.nhnacademy.processing.exception.SensorContextNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SensorContextClient {

    private final RestClient sensorContextRestClient;

    public SensorContext fetch(String devEui) {
        try {
            return sensorContextRestClient.get()
                    .uri("/internal/sensors/{devEui}/telemetry-context", devEui)
                    .retrieve()
                    .body(SensorContext.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new SensorContextNotFoundException(devEui);
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InvalidDevEuiException(devEui);
        }
    }
}