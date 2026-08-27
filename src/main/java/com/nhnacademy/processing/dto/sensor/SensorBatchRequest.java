package com.nhnacademy.processing.dto.sensor;

import java.util.List;

public record SensorBatchRequest(
        List<String> devEuis
) {
}
