package com.nhnacademy.processing.dto.sensor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SensorRoomAssignmentRequest(

        @NotBlank(message = "sensorDeviceId는 필수입니다.")
        String sensorDeviceId,

        @NotNull(message = "roomId는 필수입니다.")
        Integer roomId
) {
}
