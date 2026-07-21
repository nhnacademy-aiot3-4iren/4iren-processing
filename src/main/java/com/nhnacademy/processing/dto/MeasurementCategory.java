package com.nhnacademy.processing.dto;

public enum MeasurementCategory {
    ENVIRONMENT,        // 필터링 O, 룰 엔진 발행 O
    DEVICE_HEALTH,       // 필터링 X, 룰 엔진 발행 O
    NETWORK_QUALITY       // 필터링 X, 룰 엔진 발행 X
}
