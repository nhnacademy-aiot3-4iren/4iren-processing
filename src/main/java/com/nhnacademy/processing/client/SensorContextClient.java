package com.nhnacademy.processing.client;

import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.exception.InvalidDevEuiException;
import com.nhnacademy.processing.exception.SensorContextNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SensorContextClient {

//    private final RestClient sensorContextRestClient;

    private final CoreClient coreClient;

    public SensorContext fetch(String devEui) {
        try {
            return coreClient.fetch(devEui);
        } catch (HttpClientErrorException.NotFound e) {
            throw new SensorContextNotFoundException(devEui);
        } catch (HttpClientErrorException.BadRequest e) {
            throw new InvalidDevEuiException(devEui);
        }
    }
}