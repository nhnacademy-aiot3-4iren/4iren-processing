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
        // 리팩토링된 (client, ticker) 생성자를 사용
        resolver = new SensorContextResolver(client, ticker);
    }

    @Test
    @DisplayName("성공 응답은 캐시에서 반환되어 외부 API(client)가 한 번만 호출된다")
    void fetch_Success_IsCached() {
        // given
        SensorContext context = new SensorContext("devEui1", 12, 3);
        when(client.fetch("deveui1")).thenReturn(context);

        // when
        resolver.resolve("deveui1");
        resolver.resolve("deveui1");
        resolver.resolve("deveui1");

        // then
        verify(client, times(1)).fetch("deveui1");
    }

    @Test
    @DisplayName("미등록 센서는 빈 Optional을 반환하고 에러를 밖으로 던지지 않는다")
    void resolve_NotFound_ReturnsEmpty() {
        // given
        when(client.fetch("unknown")).thenThrow(new SensorContextNotFoundException("unknown"));

        // when
        Optional<SensorContext> result = resolver.resolve("unknown");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("API 통신 예외 발생 시에도 멈추지 않고 빈 Optional을 반환한다")
    void resolve_ApiException_ReturnsEmpty() {
        // given
        when(client.fetch("deveui2")).thenThrow(new RuntimeException("Connection refused"));

        // when
        Optional<SensorContext> result = resolver.resolve("deveui2");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("성공 캐시는 30분 이내에는 계속 유지된다")
    void cache_Success_PersistsFor30Minutes() {
        // given
        when(client.fetch("deveui3")).thenReturn(new SensorContext("deveui3", 101, 1));

        // when
        resolver.resolve("deveui3");
        ticker.advance(Duration.ofMinutes(29)); // 29분 경과 시뮬레이션
        resolver.resolve("deveui3");

        // then
        verify(client, times(1)).fetch("deveui3");
    }

    @Test
    @DisplayName("성공 캐시는 30분 경과 후 만료되어 재조회한다")
    void cache_Success_ExpiresAfter30Minutes() {
        // given
        when(client.fetch("deveui4")).thenReturn(new SensorContext("deveui4", 101, 1));

        // when
        resolver.resolve("deveui4");
        ticker.advance(Duration.ofMinutes(31)); // 31분 경과 시뮬레이션
        resolver.resolve("deveui4");

        // then
        verify(client, times(2)).fetch("deveui4"); // 만료되었으므로 2번 호출됨
    }

    @Test
    @DisplayName("실패(미등록) 캐시는 1분 경과 후 만료되어 재조회한다 (동적 등록 대응)")
    void cache_Failure_ExpiresAfter1Minute() {
        // given: 처음엔 미등록(Exception)이다가, 나중에 정상 등록됨
        when(client.fetch("deveui5"))
                .thenThrow(new SensorContextNotFoundException("deveui5"))
                .thenReturn(new SensorContext("deveui5", 101, 1));

        // when
        resolver.resolve("deveui5"); // 1차 실패 캐싱 (TTL 1분)
        ticker.advance(Duration.ofMinutes(2)); // 2분 경과 시뮬레이션
        Optional<SensorContext> result = resolver.resolve("deveui5"); // 만료되어 재시도 -> 성공

        // then
        verify(client, times(2)).fetch("deveui5");
        assertThat(result).isPresent();
    }

    /**
     * Caffeine 캐시의 TTL 테스트를 위해 나노초(nanos)를 조작할 수 있는 가짜 시계(Ticker)
     */
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