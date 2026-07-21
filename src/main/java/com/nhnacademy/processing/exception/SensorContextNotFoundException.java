package com.nhnacademy.processing.exception;

public class SensorContextNotFoundException extends RuntimeException {
    public SensorContextNotFoundException(String devEui) {
        super("등록되지 않은 센서: devEui=" + devEui);
    }
}
