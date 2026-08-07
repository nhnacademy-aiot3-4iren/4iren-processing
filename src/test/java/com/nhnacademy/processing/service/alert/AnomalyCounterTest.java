package com.nhnacademy.processing.service.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    @Test
    @DisplayName("처음 에러 발생 시 Lua 스크립트를 통해 카운트=1 반환")
    void increment_first() {
        // Lua Script 실행(execute) 시 1L 반환하도록 스터빙
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.singletonList(countKey)),
                eq("3600")
        )).thenReturn(1L);

        long count = anomalyCounter.incrementAndGet(namespace, key);

        assertEquals(1L, count);
        verify(redisTemplate, times(1)).execute(
                any(RedisScript.class),
                eq(Collections.singletonList(countKey)),
                eq("3600")
        );
    }

    @Test
    @DisplayName("두 번째 에러 발생 시 Lua 스크립트를 통해 카운트=2 반환")
    void increment_again() {
        // Lua Script 실행(execute) 시 2L 반환하도록 스터빙
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.singletonList(countKey)),
                eq("3600")
        )).thenReturn(2L);

        long count = anomalyCounter.incrementAndGet(namespace, key);

        assertEquals(2L, count);
    }

    @Test
    @DisplayName("쿨다운 설정 성공 시 true 반환")
    void tryMarkAlerted_success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(alertedKey), eq("1"), any(Duration.class)))
                .thenReturn(true);

        boolean result = anomalyCounter.tryMarkAlerted(namespace, key);

        assertTrue(result);
    }

    @Test
    @DisplayName("이미 쿨다운 시 false 반환")
    void tryMarkAlerted_Failure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(alertedKey), eq("1"), any(Duration.class)))
                .thenReturn(false);

        boolean result = anomalyCounter.tryMarkAlerted(namespace, key);

        assertFalse(result);
    }

    @Test
    @DisplayName("reset 호출 시 count와 alerted 키 모두 삭제")
    void test_reset() {
        anomalyCounter.reset(namespace, key);

        verify(redisTemplate, times(1)).delete(countKey);
        verify(redisTemplate, times(1)).delete(alertedKey);
    }
}