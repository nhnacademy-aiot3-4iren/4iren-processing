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
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
        device = new DeviceIdentity("applicationId", "applicationName", "deviceProfileId", "deviceName", "devEui1234567890", 101, "location", "point");
        now = Instant.now();
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("roomId가 null인 경우 빈 Optional 반환")
    void updateContext_InvalidRoomId() {
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(), now);
        Optional<EnvironmentContext> result = service.updateContext(message, null);
        assertThat(result).isEmpty();
        verifyNoInteractions(hashOperations);
    }

    @Test
    @DisplayName("환경 데이터 정상 수신 시 Redis Hash 갱신 및 Context 반환")
    void updateContext_Success() {
        int roomId = 101;
        String key = "env:context:" + roomId;
        List<SensorData> envData = List.of(
                new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0),
                new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0)
        );
        ParsedSensorMessage message = new ParsedSensorMessage(device, envData, now);

        EnvironmentContext.MetricInfo tempInfo = new EnvironmentContext.MetricInfo("temperature", 25.0, "devEui1234567890", now);
        EnvironmentContext.MetricInfo co2Info = new EnvironmentContext.MetricInfo("co2", 1000.0, "devEui1234567890", now);

        given(hashOperations.entries(key)).willReturn(Map.of("temperature", tempInfo, "co2", co2Info));

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        verify(hashOperations, times(1)).putAll(eq(key), updatesCaptor.capture());

        Map<String, EnvironmentContext.MetricInfo> capturedUpdates = updatesCaptor.getValue();
        assertThat(capturedUpdates).hasSize(2);
        assertThat(capturedUpdates.get("temperature").value()).isEqualTo(25.0);
        assertThat(capturedUpdates.get("co2").value()).isEqualTo(1000.0);

        EnvironmentContext context = result.get();
        assertThat(context.roomId()).isEqualTo(roomId);
        assertThat(context.metrics()).hasSize(2);
    }

    @Test
    @DisplayName("Redis 연결 실패 시 예외 전파")
    void updateContext_RedisException_Propagates() {
        int roomId = 101;
        String key = "env:context:" + roomId;
        List<SensorData> envData = List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0));
        ParsedSensorMessage message = new ParsedSensorMessage(device, envData, now);

        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(hashOperations).putAll(eq(key), anyMap());

        assertThatThrownBy(() -> service.updateContext(message, roomId))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("방의 전체 환경 컨텍스트 조회 성공")
    void getContext_Success() {
        int roomId = 101;
        String key = "env:context:" + roomId;
        EnvironmentContext.MetricInfo tempInfo = new EnvironmentContext.MetricInfo("temperature", 24.0, "dev1", now);
        given(hashOperations.entries(key)).willReturn(Map.of("temperature", tempInfo));

        Optional<EnvironmentContext> contextOpt = service.getContext(roomId);

        assertThat(contextOpt).isPresent();
        assertThat(contextOpt.get().roomId()).isEqualTo(roomId);
        assertThat(contextOpt.get().metrics()).hasSize(1);
    }
}