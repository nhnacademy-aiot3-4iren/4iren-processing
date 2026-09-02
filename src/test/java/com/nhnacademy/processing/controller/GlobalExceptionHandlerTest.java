package com.nhnacademy.processing.controller;

import com.nhnacademy.processing.exception.MqttBrokerConnectionException;
import com.nhnacademy.processing.exception.MqttBrokerNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResponseStatusException 처리")
    void handleResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "권한이 없습니다.");
        ResponseEntity<Map<String, String>> response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "권한이 없습니다.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 처리")
    void handleValidationException() {
        MethodParameter parameter = mock(MethodParameter.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("dto", "devEui", "devEui는 필수입니다.");

        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "devEui: devEui는 필수입니다.");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 처리 - 에러 목록이 비어있을 때 기본 메시지")
    void handleValidationException_EmptyErrors() {
        MethodParameter parameter = mock(MethodParameter.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "요청 값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("MqttBrokerConnectionException 처리")
    void handleMqttBrokerConnectionException() {
        MqttBrokerConnectionException ex = new MqttBrokerConnectionException("연결 실패", new RuntimeException());
        ResponseEntity<Map<String, String>> response = handler.handleMqttBrokerConnectionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "연결 실패");
    }

    @Test
    @DisplayName("MqttBrokerNotFoundException 처리")
    void handleMqttBrokerNotFoundException() {
        MqttBrokerNotFoundException ex = new MqttBrokerNotFoundException(1L);
        ResponseEntity<Map<String, String>> response = handler.handleMqttBrokerNotFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", ex.getMessage());
    }

    @Test
    @DisplayName("NoResourceFoundException 처리")
    void handleNoResourceFoundException() {
        NoResourceFoundException ex = new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/wrong-url");
        ResponseEntity<Map<String, String>> response = handler.handleNoResourceFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    @DisplayName("DataIntegrityViolationException 처리")
    void handleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("중복 키 오류");
        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "이미 존재하는 리소스이거나 제약조건을 위반했습니다.");
    }

    @Test
    @DisplayName("일반 Exception 처리")
    void handleException() {
        Exception ex = new RuntimeException("알 수 없는 서버 에러");
        ResponseEntity<Map<String, String>> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "알 수 없는 서버 에러");
    }
}