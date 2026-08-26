package com.nhnacademy.processing.dto.sensor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SensorRoomAssignmentRequest(

        @NotBlank(message = "devEui는 필수입니다.")
        String devEui,

        @NotNull(message = "buildingId는 필수입니다.")
        Long buildingId,

        @NotNull(message = "roomId는 필수입니다.")
        Integer roomId
) {}