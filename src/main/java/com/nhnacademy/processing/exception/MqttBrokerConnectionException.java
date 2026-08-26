package com.nhnacademy.processing.exception;

public class MqttBrokerConnectionException extends RuntimeException {
    public MqttBrokerConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
