package com.nhnacademy.processing.dto;

public record RawSensorMessage(
        Long brokerId,
        String topic,
        String payload
) {}