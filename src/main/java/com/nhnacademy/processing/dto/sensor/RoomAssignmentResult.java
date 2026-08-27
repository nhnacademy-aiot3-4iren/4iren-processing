package com.nhnacademy.processing.dto.sensor;

public record RoomAssignmentResult(
        String devEui,
        Long brokerId,
        Integer roomId
) {
}
