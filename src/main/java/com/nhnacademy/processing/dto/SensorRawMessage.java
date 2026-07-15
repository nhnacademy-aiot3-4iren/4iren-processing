package com.nhnacademy.processing.dto;

public record SensorRawMessage(
        Long brokerId,
        String topic,
        String payload
) {}