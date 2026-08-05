package com.nhnacademy.processing.service.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyCounterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AnomalyCounter anomalyCounter;

    private final String namespace = "test";
    private final String key = "dev123:temp";
    private final String countKey = namespace + ":count:" + key;
    private final String alertedKey = namespace + ":alerted:" + key;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("처음 에러 발생 시 카운트=1, 윈도우 TTL 설정")
    void increment_first() {
        when(valueOperations.increment(countKey)).thenReturn(1L);

        long count = anomalyCounter.incrementAndGet(namespace, key);

        assertEquals(1L, count);
        verify(redisTemplate, times(1)).expire(eq(countKey), any(Duration.class));
    }

    @Test
    @DisplayName("두 번째 에러 발생 시 카운트는 증가, 윈도우 TTL은 재설정 X")
    void increment_again() {
        when(valueOperations.increment(countKey)).thenReturn(2L);

        long count = anomalyCounter.incrementAndGet(namespace, key);

        assertEquals(2L, count);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("쿨다운 설정 성공 시 true 반환")
    void tryMarkAlerted_success() {
        when(valueOperations.setIfAbsent(eq(alertedKey), eq("1"), any(Duration.class)))
                .thenReturn(true);

        boolean result = anomalyCounter.tryMarkAlerted(namespace, key);

        assertTrue(result);
    }

    @Test
    @DisplayName("이미 쿨다운 시 false 반환")
    void tryMarkAlerted_Failure() {
        when(valueOperations.setIfAbsent(eq(alertedKey), eq("1"), any(Duration.class)))
                .thenReturn(false);

        boolean result = anomalyCounter.tryMarkAlerted(namespace, key);

        assertFalse(result);
    }

    @Test
    @DisplayName("reset 호출 시 count와 alerted 키 모두 삭제")
    void test_reset() {
        anomalyCounter.reset(namespace,key);

        verify(redisTemplate, times(1)).delete(countKey);
        verify(redisTemplate, times(1)).delete(alertedKey);
    }
}