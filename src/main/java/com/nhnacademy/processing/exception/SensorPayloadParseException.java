package com.nhnacademy.processing.exception;

public class SensorPayloadParseException extends RuntimeException {
    public SensorPayloadParseException(String message) {
        super(message);
    }

    public SensorPayloadParseException(String message, Throwable cause) {
        super(message, cause);
    }
}