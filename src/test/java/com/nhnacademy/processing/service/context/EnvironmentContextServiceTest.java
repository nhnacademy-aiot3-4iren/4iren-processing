package com.nhnacademy.processing.service.context;

import com.nhnacademy.processing.dto.context.EnvironmentContext;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentContextServiceTest {

    @Mock
    private RedisTemplate<String, EnvironmentContext.MetricInfo> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private EnvironmentContextService service;

    @Captor
    private ArgumentCaptor<Map<String, EnvironmentContext.MetricInfo>> updatesCaptor;

    private DeviceIdentity device;
    private Instant now;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ttlMinutes", 30L);
        device = new DeviceIdentity("app", "appName", "prof", "devName", "devEui123", 101, "loc", "pt");
        now = Instant.now();
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("roomId가 null이거나 device가 null이면 Optional.empty 반환")
    void updateContext_NullRoomIdOrDevice_ReturnsEmpty() {
        ParsedSensorMessage messageWithDevice = new ParsedSensorMessage(device, List.of(), now);
        assertThat(service.updateContext(messageWithDevice, null)).isEmpty();

        ParsedSensorMessage messageWithoutDevice = new ParsedSensorMessage(null, List.of(), now);
        assertThat(service.updateContext(messageWithoutDevice, 101)).isEmpty();

        verifyNoInteractions(hashOperations);
    }

    @Test
    @DisplayName("ENVIRONMENT, DEVICE_HEALTH 데이터가 없으면 Optional.empty 반환")
    void updateContext_NoValidCategoryData_ReturnsEmpty() {
        SensorData networkData = new SensorData(MeasurementCategory.NETWORK_QUALITY, "rssi", -60.0);
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(networkData), now);

        assertThat(service.updateContext(message, 101)).isEmpty();
        verifyNoInteractions(hashOperations);
    }

    @Test
    @DisplayName("정상 데이터가 들어오면 Redis Hash를 갱신하고 컨텍스트를 반환한다 (measuredAt null 처리 포함)")
    void updateContext_Success() {
        int roomId = 101;
        String key = "env:context:" + roomId;

        SensorData envData = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0);
        SensorData healthData = new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 100.0);
        // measuredAt을 null로 전달하여 Instant.now() 분기 커버
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(envData, healthData), null);

        EnvironmentContext.MetricInfo tempInfo = new EnvironmentContext.MetricInfo("temperature", 25.0, "devEui123", Instant.now());
        when(hashOperations.entries(key)).thenReturn(Map.of("temperature", tempInfo));

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        verify(hashOperations, times(1)).putAll(eq(key), updatesCaptor.capture());
        verify(redisTemplate, times(1)).expire(key, Duration.ofMinutes(30L));

        Map<String, EnvironmentContext.MetricInfo> captured = updatesCaptor.getValue();
        assertThat(captured).hasSize(2).containsKeys("temperature", "battery");
    }

    @Test
    @DisplayName("WRONGTYPE 에러 발생 시 기존 키를 삭제하고 1회 재시도하여 성공한다")
    void updateContext_WrongTypeRecovery_Success() {
        int roomId = 101;
        String key = "env:context:" + roomId;
        ParsedSensorMessage message = new ParsedSensorMessage(device,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0)), now);

        RedisCommandExecutionException lettuceEx = new RedisCommandExecutionException("WRONGTYPE Operation against a key holding the wrong kind of value");
        RedisSystemException springEx = new RedisSystemException("Redis execute exception", lettuceEx);

        // 첫 번째 putAll은 예외 발생, 두 번째는 성공
        doThrow(springEx).doNothing().when(hashOperations).putAll(eq(key), anyMap());

        service.updateContext(message, roomId);

        verify(hashOperations, times(2)).putAll(eq(key), anyMap());
        verify(redisTemplate, times(1)).delete(key);
        verify(redisTemplate, times(1)).expire(key, Duration.ofMinutes(30L));
    }

    @Test
    @DisplayName("WRONGTYPE 에러가 재시도(2회차)에서도 발생하면 예외를 던진다")
    void updateContext_WrongTypeRecovery_FailsSecondTime() {
        int roomId = 101;
        String key = "env:context:" + roomId;
        ParsedSensorMessage message = new ParsedSensorMessage(device,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0)), now);

        RedisCommandExecutionException lettuceEx = new RedisCommandExecutionException("WRONGTYPE Operation");
        RedisSystemException springEx = new RedisSystemException("Error", lettuceEx);

        // 계속 예외 발생
        doThrow(springEx).when(hashOperations).putAll(eq(key), anyMap());

        assertThatThrownBy(() -> service.updateContext(message, roomId))
                .isInstanceOf(RedisSystemException.class);

        verify(hashOperations, times(2)).putAll(eq(key), anyMap());
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    @DisplayName("WRONGTYPE이 아닌 일반 Redis 예외는 재시도 없이 즉시 예외를 던진다")
    void updateContext_OtherRedisException_Propagates() {
        int roomId = 101;
        String key = "env:context:" + roomId;
        ParsedSensorMessage message = new ParsedSensorMessage(device,
                List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0)), now);

        // 원인이 WRONGTYPE이 아닌 예외
        RedisSystemException springEx = new RedisSystemException("Connection timeout", new RuntimeException("Timeout"));

        doThrow(springEx).when(hashOperations).putAll(eq(key), anyMap());

        assertThatThrownBy(() -> service.updateContext(message, roomId))
                .isInstanceOf(RedisSystemException.class);

        verify(hashOperations, times(1)).putAll(eq(key), anyMap());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("roomId가 null이면 getContext는 Optional.empty 반환")
    void getContext_NullRoomId_ReturnsEmpty() {
        assertThat(service.getContext(null)).isEmpty();
    }

    @Test
    @DisplayName("Hash 엔트리가 비어있거나 null이면 getContext는 Optional.empty 반환")
    void getContext_EmptyHash_ReturnsEmpty() {
        int roomId = 101;
        String key = "env:context:" + roomId;

        when(hashOperations.entries(key)).thenReturn(null);
        assertThat(service.getContext(roomId)).isEmpty();

        when(hashOperations.entries(key)).thenReturn(Collections.emptyMap());
        assertThat(service.getContext(roomId)).isEmpty();
    }

    @Test
    @DisplayName("Hash에 데이터가 있으면 가장 최신 updatedAt을 찾아 Context를 반환한다")
    void getContext_Success() {
        int roomId = 101;
        String key = "env:context:" + roomId;

        Instant olderTime = Instant.now().minusSeconds(60);
        Instant newerTime = Instant.now();

        EnvironmentContext.MetricInfo oldInfo = new EnvironmentContext.MetricInfo("co2", 400.0, "dev1", olderTime);
        EnvironmentContext.MetricInfo newInfo = new EnvironmentContext.MetricInfo("temperature", 25.0, "dev2", newerTime);

        when(hashOperations.entries(key)).thenReturn(Map.of("co2", oldInfo, "temperature", newInfo));

        Optional<EnvironmentContext> result = service.getContext(roomId);

        assertThat(result).isPresent();
        assertThat(result.get().roomId()).isEqualTo(roomId);
        assertThat(result.get().metrics()).hasSize(2);
        assertThat(result.get().updatedAt()).isEqualTo(newerTime); // 가장 최신 시간 선택 검증
    }
}