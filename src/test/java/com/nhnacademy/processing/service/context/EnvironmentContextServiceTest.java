package com.nhnacademy.processing.service.context;

import com.nhnacademy.processing.dto.context.EnvironmentContext;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentContextServiceTest {

    private static final int MAX_RETRY = 3;

    @Mock private RedisTemplate<String, EnvironmentContext> redisTemplate;
    @Mock private RedisOperations<String, EnvironmentContext> redisOperations;
    @Mock private ValueOperations<String, EnvironmentContext> valueOperations;

    @InjectMocks
    private EnvironmentContextService service;

    @Captor
    private ArgumentCaptor<EnvironmentContext> contextCaptor;

    private DeviceIdentity device;
    private Instant now;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        device = new DeviceIdentity("applicationId", "applicationName", "deviceProfileId", "deviceName", "devEui1234567890", 101, "point");
        now = Instant.now();

        lenient().when(redisTemplate.execute(any(SessionCallback.class)))
                .thenAnswer(invocation -> {
                    SessionCallback<?> callback = invocation.getArgument(0);
                    return callback.execute(redisOperations);
                });
        lenient().when(redisOperations.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisOperations.exec()).thenReturn(List.of("OK"));
    }

    @Test
    @DisplayName("roomId가 null이거나 음수면 빈 Optional 반환")
    void updateContext_InvalidRoomId() {
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(), now);

        Optional<EnvironmentContext> resultNull = service.updateContext(message, null);
        Optional<EnvironmentContext> resultNegative = service.updateContext(message, -1);

        assertThat(resultNull).isEmpty();
        assertThat(resultNegative).isEmpty();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("기존 컨텍스트가 없을 때 새로운 컨텍스트를 생성하여 저장")
    void updateContext_NoExistingContext() {
        int roomId = 101;
        List<SensorData> envData = List.of(
                new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0),
                new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0));
        ParsedSensorMessage message = new ParsedSensorMessage(device, envData, now);

        given(valueOperations.get("env:context:" + roomId)).willReturn(null);

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        verify(valueOperations).set(eq("env:context:" + roomId), contextCaptor.capture());

        EnvironmentContext savedContext = contextCaptor.getValue();
        assertThat(savedContext.roomId()).isEqualTo(roomId);
        assertThat(savedContext.metrics()).hasSize(2);
        assertThat(savedContext.metrics()).anyMatch(m -> m.metric().equals("temperature") && m.value().equals(25.0));
        assertThat(savedContext.metrics()).anyMatch(m -> m.metric().equals("co2") && m.value().equals(1000.0));
    }

    @Test
    @DisplayName("기존 컨텍스트가 존재할 때 병합하여 저장")
    void updateContext_Merge() {
        int roomId = 101;
        List<SensorData> newEnvData = List.of(
                new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 26.0),
                new SensorData(MeasurementCategory.ENVIRONMENT, "humidity", 50.0));
        ParsedSensorMessage message = new ParsedSensorMessage(device, newEnvData, now);

        EnvironmentContext existingContext = new EnvironmentContext(
                roomId,
                List.of(
                        new EnvironmentContext.MetricInfo("temperature", 22.0, "old-dev-eui", now.minusSeconds(60)),
                        new EnvironmentContext.MetricInfo("co2", 400.0, "other-dev-eui", now.minusSeconds(60))
                ),
                now.minusSeconds(60)
        );
        given(valueOperations.get("env:context:" + roomId)).willReturn(existingContext);

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        verify(valueOperations).set(eq("env:context:" + roomId), contextCaptor.capture());

        EnvironmentContext mergedContext = contextCaptor.getValue();
        assertThat(mergedContext.metrics()).hasSize(3);
        assertThat(mergedContext.metrics()).anyMatch(m ->
                m.metric().equals("temperature") && m.value().equals(26.0) && m.devEui().equals("devEui1234567890"));
        assertThat(mergedContext.metrics()).anyMatch(m -> m.metric().equals("co2") && m.value().equals(400.0));
        assertThat(mergedContext.metrics()).anyMatch(m -> m.metric().equals("humidity") && m.value().equals(50.0));
    }

    @Test
    @DisplayName("Redis 조회 중 예외가 발생하면 그대로 전파 (호출부에서 sensorErrorChannel로 라우팅)")
    void updateContext_RedisException_Propagates() {
        int roomId = 101;
        List<SensorData> envData = List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0));
        ParsedSensorMessage message = new ParsedSensorMessage(device, envData, now);

        given(valueOperations.get("env:context:" + roomId))
                .willThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> service.updateContext(message, roomId))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("EXEC가 WATCH 충돌로 1차 실패해도 재시도 시 최신 값을 다시 읽어 병합")
    void updateContext_RetriesOnWatchConflict_ThenMergesLatestValue() {
        int roomId = 101;
        ParsedSensorMessage message = new ParsedSensorMessage(device,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 26.0)), now);

        EnvironmentContext staleExisting = new EnvironmentContext(roomId,
                List.of(new EnvironmentContext.MetricInfo("co2", 500.0, "devEuiX", now.minusSeconds(30))),
                now.minusSeconds(30));

        EnvironmentContext concurrentlyUpdatedExisting = new EnvironmentContext(roomId,
                List.of(
                        new EnvironmentContext.MetricInfo("co2", 500.0, "devEuiX", now.minusSeconds(30)),
                        new EnvironmentContext.MetricInfo("humidity", 50.0, "devEuiY", now.minusSeconds(5))
                ),
                now.minusSeconds(5));

        given(valueOperations.get("env:context:" + roomId))
                .willReturn(staleExisting)
                .willReturn(concurrentlyUpdatedExisting);

        given(redisOperations.exec())
                .willReturn(List.of())
                .willReturn(List.of("OK"));

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        EnvironmentContext merged = result.get();

        assertThat(merged.metrics()).hasSize(3);
        assertThat(merged.metrics()).anyMatch(m -> m.metric().equals("co2") && m.value().equals(500.0));
        assertThat(merged.metrics()).anyMatch(m -> m.metric().equals("humidity") && m.value().equals(50.0));
        assertThat(merged.metrics()).anyMatch(m -> m.metric().equals("temperature") && m.value().equals(26.0));

        verify(redisOperations, times(2)).watch("env:context:" + roomId);
        verify(redisOperations, times(2)).exec();
        verify(valueOperations, times(2)).get("env:context:" + roomId);
    }

    @Test
    @DisplayName("EXEC 충돌이 MAX_RETRY 횟수를 초과하면 예외")
    void updateContext_ExceedsMaxRetry_ThrowsException() {
        int roomId = 101;
        ParsedSensorMessage message = new ParsedSensorMessage(device,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 26.0)), now);

        given(valueOperations.get(anyString())).willReturn(null);
        given(redisOperations.exec()).willReturn(List.of());

        assertThatThrownBy(() -> service.updateContext(message, roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roomId=" + roomId);

        verify(redisOperations, times(MAX_RETRY)).exec();
        verify(redisOperations, times(MAX_RETRY)).watch("env:context:" + roomId);
        verify(valueOperations, times(MAX_RETRY)).set(eq("env:context:" + roomId), any(EnvironmentContext.class));
    }

    @Test
    @DisplayName("두 스레드가 같은 roomId의 서로 다른 metric을 동시에 갱신해도 두 갱신 모두 반영")
    void updateContext_ConcurrentUpdatesToDifferentMetrics_NoLostUpdate() throws Exception {
        int roomId = 101;
        String key = "env:context:" + roomId;

        FakeOptimisticLockStore fakeStore = new FakeOptimisticLockStore();

        willAnswer(invocation -> {
            fakeStore.watch(invocation.getArgument(0));
            return null;
        }).given(redisOperations).watch(anyString());

        willAnswer(invocation -> {
            fakeStore.queueSet(invocation.getArgument(1));
            return null;
        }).given(valueOperations).set(anyString(), any());

        given(valueOperations.get(anyString()))
                .willAnswer(invocation -> fakeStore.get(invocation.getArgument(0)));
        given(redisOperations.exec())
                .willAnswer(invocation -> fakeStore.exec());

        DeviceIdentity deviceA = new DeviceIdentity("app", "app", "profile", "device-A", "devEuiA", roomId, "point");
        DeviceIdentity deviceB = new DeviceIdentity("app", "app", "profile", "device-B", "devEuiB", roomId, "point");
        ParsedSensorMessage messageA = new ParsedSensorMessage(deviceA,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 26.0)), now);
        ParsedSensorMessage messageB = new ParsedSensorMessage(deviceB,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "humidity", 50.0)), now);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Optional<EnvironmentContext>> taskA = () -> {
            ready.countDown();
            start.await();
            return service.updateContext(messageA, roomId);
        };
        Callable<Optional<EnvironmentContext>> taskB = () -> {
            ready.countDown();
            start.await();
            return service.updateContext(messageB, roomId);
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Optional<EnvironmentContext>> futureA = pool.submit(taskA);
            Future<Optional<EnvironmentContext>> futureB = pool.submit(taskB);

            boolean isReady = ready.await(2, TimeUnit.SECONDS);
            assertThat(isReady).isTrue();
            start.countDown();

            Optional<EnvironmentContext> resultA = futureA.get(5, TimeUnit.SECONDS);
            Optional<EnvironmentContext> resultB = futureB.get(5, TimeUnit.SECONDS);

            assertThat(resultA).isPresent();
            assertThat(resultB).isPresent();
        }

        EnvironmentContext finalContext = fakeStore.get(key);
        assertThat(finalContext).isNotNull();
        assertThat(finalContext.metrics()).anyMatch(m -> m.metric().equals("temperature") && m.value().equals(26.0));
        assertThat(finalContext.metrics()).anyMatch(m -> m.metric().equals("humidity") && m.value().equals(50.0));
    }

    private static class FakeOptimisticLockStore {
        private final Map<String, EnvironmentContext> data = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> versions = new ConcurrentHashMap<>();
        private final ThreadLocal<String> watchedKey = new ThreadLocal<>();
        private final ThreadLocal<Long> watchedVersion = new ThreadLocal<>();
        private final ThreadLocal<EnvironmentContext> pendingValue = new ThreadLocal<>();

        EnvironmentContext get(String key) {
            return data.get(key);
        }

        void watch(String key) {
            watchedKey.set(key);
            watchedVersion.set(versions.computeIfAbsent(key, k -> new AtomicLong(0)).get());
        }

        void queueSet(EnvironmentContext value) {
            pendingValue.set(value);
        }

        List<Object> exec() {
            String key = watchedKey.get();
            Long expectedVersion = watchedVersion.get();
            EnvironmentContext valueToSet = pendingValue.get();
            try {
                if (key == null || valueToSet == null) {
                    return List.of();
                }
                AtomicLong versionHolder = versions.computeIfAbsent(key, k -> new AtomicLong(0));
                synchronized (versionHolder) {
                    if (versionHolder.get() != expectedVersion) {
                        return List.of();
                    }
                    data.put(key, valueToSet);
                    versionHolder.incrementAndGet();
                    return List.of("OK");
                }
            } finally {
                watchedKey.remove();
                watchedVersion.remove();
                pendingValue.remove();
            }
        }
    }
}