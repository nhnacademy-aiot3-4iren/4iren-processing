package com.nhnacademy.processing.service.process;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.nhnacademy.processing.client.SensorContextClient;
import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.exception.SensorContextNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SensorContextResolver {

    private final SensorContextClient client;
    private final Cache<String, Optional<SensorContext>> cache;

    @Autowired
    public SensorContextResolver(SensorContextClient client) {
        this(client, Ticker.systemTicker());
    }

    public SensorContextResolver(SensorContextClient client, Ticker ticker) {
        this.client = client;
        this.cache = Caffeine.newBuilder()
                .ticker(ticker)
                .maximumSize(10_000)
                .expireAfter(new Expiry<String, Optional<SensorContext>>() {
                    @Override
                    public long expireAfterCreate(String key, Optional<SensorContext> value, long currentTime) {
                        return value.isPresent()
                                ? TimeUnit.MINUTES.toNanos(30)
                                : TimeUnit.MINUTES.toNanos(1);
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
