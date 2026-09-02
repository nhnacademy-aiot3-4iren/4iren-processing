package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.exception.MqttBrokerConnectionException;
import com.nhnacademy.processing.exception.MqttBrokerNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MESSAGE_KEY = "message";

    // 1. 인터셉터 / 리졸버에서 발생한 ResponseStatusException (400, 401, 403 등 본래 상태 코드 유지)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException e) {
        log.warn("ResponseStatusException: status={}, reason={}", e.getStatusCode(), e.getReason());
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of(MESSAGE_KEY, e.getReason() != null ? e.getReason() : e.getMessage()));
    }

    // 2. @Valid DTO 검증 실패 처리 -> 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        log.warn("MethodArgumentNotValidException: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(MESSAGE_KEY, errorMessage));
    }

    // 3. MQTT 브로커 정보(URL/username/password/topic)가 잘못되어 구독(연결) 자체에 실패한 경우 -> 400 Bad Request
    @ExceptionHandler(MqttBrokerConnectionException.class)
    public ResponseEntity<Map<String, String>> handleMqttBrokerConnectionException(MqttBrokerConnectionException e) {
        log.warn("MqttBrokerConnectionException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(MESSAGE_KEY, e.getMessage()));
    }

    @ExceptionHandler(MqttBrokerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMqttBrokerNotFoundException(MqttBrokerNotFoundException e) {
        log.warn("MqttBrokerNotFoundException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(MESSAGE_KEY, e.getMessage()));
    }

    // 4. 그 외 서버 내부 예외(IllegalStateException 등) -> 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(MESSAGE_KEY, e.getMessage() != null ? e.getMessage() : "서버 내부 오류가 발생했습니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(MESSAGE_KEY, e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 위반(중복 키 등): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(MESSAGE_KEY, "이미 존재하는 리소스이거나 제약조건을 위반했습니다."));
    }
}