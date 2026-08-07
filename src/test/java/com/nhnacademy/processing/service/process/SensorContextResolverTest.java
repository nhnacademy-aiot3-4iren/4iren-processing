package com.nhnacademy.processing.service.process;

import com.github.benmanes.caffeine.cache.Ticker;
import com.nhnacademy.processing.client.SensorContextClient;
import com.nhnacademy.processing.dto.api.SensorContext;
import com.nhnacademy.processing.exception.SensorContextNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorContextResolverTest {

    @Mock
    private SensorContextClient client;

    private FakeTicker ticker;
    private SensorContextResolver resolver;

    @BeforeEach
    void setUp() {
        ticker = new FakeTicker();
        resolver = new SensorContextResolver(client, ticker);
    }

    @Test
    @DisplayName("성공 응답은 캐시에서 반환, 외부 API 한 번만 호출")
    void fetch_Success_IsCached() {
        SensorContext context = new SensorContext("devEui1", 12, 3);
        when(client.fetch("devEui1")).thenReturn(context);

        resolver.resolve("devEui1");
        resolver.resolve("devEui1");
        resolver.resolve("devEui1");

        verify(client, times(1)).fetch("devEui1");
    }

    @Test
    @DisplayName("미등록 센서는 빈 Optional을 반환")
    void resolve_NotFound_ReturnsEmpty() {
        when(client.fetch("unknown")).thenThrow(new SensorContextNotFoundException("unknown"));

        Optional<SensorContext> result = resolver.resolve("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("API 통신 예외 발생 시에도 멈추지 않고 빈 Optional을 반환")
    void resolve_ApiException_ReturnsEmpty() {
        when(client.fetch("devEui2")).thenThrow(new RuntimeException("Connection refused"));

        Optional<SensorContext> result = resolver.resolve("devEui2");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("성공 캐시는 30분 유지")
    void cache_Success_PersistsFor30Minutes() {
        when(client.fetch("devEui3")).thenReturn(new SensorContext("devEui3", 101, 1));

        resolver.resolve("devEui3");
        ticker.advance(Duration.ofMinutes(29));
        resolver.resolve("devEui3");

        verify(client, times(1)).fetch("devEui3");
    }

    @Test
    @DisplayName("성공 캐시는 30분 경과 후 만료")
    void cache_Success_ExpiresAfter30Minutes() {
        when(client.fetch("devEui4")).thenReturn(new SensorContext("devEui4", 101, 1));

        // when
        resolver.resolve("devEui4");
        ticker.advance(Duration.ofMinutes(31));
        resolver.resolve("devEui4");

        // then
        verify(client, times(2)).fetch("devEui4");
    }

    @Test
    @DisplayName("실패 캐시는 1분 경과 후 만료되어 재조회")
    void cache_Failure_ExpiresAfter1Minute() {
        when(client.fetch("devEui5"))
                .thenThrow(new SensorContextNotFoundException("devEui5"))
                .thenReturn(new SensorContext("devEui5", 101, 1));

        resolver.resolve("devEui5");
        ticker.advance(Duration.ofMinutes(2));
        Optional<SensorContext> result = resolver.resolve("devEui5");

        verify(client, times(2)).fetch("devEui5");
        assertThat(result).isPresent();
    }

    static class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}