package com.nhnacademy.processing.service.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis INCR/EXPIRE, SET NX EX를 감싼 범용 카운터/쿨다운 유틸
 *
 * 사용하는 키 형태:
 *   {namespace}:count:{key}    -- INCR, 최초 증가 시에만 TTL(window) 설정 (fixed window)
 *   {namespace}:alerted:{key}  -- SET NX EX(cooldown), 있으면 이미 알린 상태
 */
@Component
@RequiredArgsConstructor
public class AnomalyCounter {

    private final Duration window = Duration.ofHours(1);
    private final Duration cooldown = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;

    public long incrementAndGet(String namespace, String key) {
        String redisKey = countKey(namespace, key);
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count == null ? 0L : count;
    }

    public boolean tryMarkAlerted(String namespace, String key) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(alertedKey(namespace, key), "1", cooldown);
        return Boolean.TRUE.equals(success);
    }

    public void reset(String namespace, String key) {
        redisTemplate.delete(countKey(namespace, key));
        redisTemplate.delete(alertedKey(namespace, key));
    }

    private String countKey(String namespace, String key) {
        return namespace + ":count:" + key;
    }

    private String alertedKey(String namespace, String key) {
        return namespace + ":alerted:" + key;
    }
}
