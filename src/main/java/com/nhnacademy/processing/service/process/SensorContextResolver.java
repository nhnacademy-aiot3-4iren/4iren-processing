package com.nhnacademy.processing.service.process;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.nhnacademy.processing.client.SensorContextClient;
import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.exception.SensorContextNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SensorContextResolver {

    private final SensorContextClient client;
    private final Cache<String, Optional<SensorContext>> cache;

    public SensorContextResolver(SensorContextClient client) {
        this.client = client;

        this.cache = Caffeine.newBuilder()
                .maximumSize(1000) // 최대 1000개 캐싱 (초과 시 오래된 것부터 방출)
                .expireAfter(new Expiry<String, Optional<SensorContext>>() {    // TTL을 상황에 맞게 동적으로 설정하는 인터페이스
                    @Override
                    public long expireAfterCreate(String key, Optional<SensorContext> value, long currentTime) {
                        return value.isPresent()
                                ? TimeUnit.MINUTES.toNanos(60)  // 정상 조회 시 60분 동안 캐싱
                                : TimeUnit.MINUTES.toNanos(1);  // 미등록/장애 시 1분만 캐싱 빠른 재시도 유도
                    }

                    @Override
                    public long expireAfterUpdate(String key, Optional<SensorContext> value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, Optional<SensorContext> value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    public Optional<SensorContext> resolve(String devEui) {
        return cache.get(devEui, key -> {
            try {
                return Optional.of(client.fetch(key));
            } catch (SensorContextNotFoundException e) {
                log.warn("미등록 센서 devEui({}), roomId 없이 처리", key);
                return Optional.empty();
            } catch (Exception e) {
                log.error("Sensor Context API 호출 실패: devEui({})", key, e);
                return Optional.empty();
            }
        });
    }
}
