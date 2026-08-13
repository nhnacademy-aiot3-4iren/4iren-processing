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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EnvironmentContextServiceTest {

    @Mock private RedisTemplate<String, EnvironmentContext> redisTemplate;
    @Mock private ValueOperations<String, EnvironmentContext> valueOperations;

    @InjectMocks
    private EnvironmentContextService service;

    @Captor
    private ArgumentCaptor<EnvironmentContext> contextCaptor;

    private DeviceIdentity device;
    private Instant now;

    @BeforeEach
    void setUp() {
        device = new DeviceIdentity("applicationId", "applicationName", "deviceProfileId", "deviceName", "devEui1234567890", 101, "point");
        now = Instant.now();
    }

    @Test
    @DisplayName("roomId가 null이거나 음수면 빈 Optional 반환")
    void updateContext_InvalidRoomId() {
        ParsedSensorMessage message = new ParsedSensorMessage(device, List.of(), now);

        Optional<EnvironmentContext> resultNull = service.updateContext(message, null);
        Optional<EnvironmentContext> resultNegative = service.updateContext(message, -1);

        assertThat(resultNull).isEmpty();
        assertThat(resultNegative).isEmpty();
    }

    @Test
    @DisplayName("메시지에 ENVIRONMENT 데이터가 없으면 빈 Optional 반환")
    void updateContext_NoEnvironmentData() {
        List<SensorData> noEnvData = List.of(new SensorData(MeasurementCategory.DEVICE_HEALTH, "battery", 100.0));
        ParsedSensorMessage message = new ParsedSensorMessage(device, noEnvData, now);

        Optional<EnvironmentContext> result = service.updateContext(message, 101);

        assertThat(result).isEmpty();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("기존 컨텍스트가 없을 때 새로운 컨텍스트를 생성하여 저장")
    void updateContext_NoExistingContext() {
        int roomId = 101;
        List<SensorData> envData = List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0),
                new SensorData(MeasurementCategory.ENVIRONMENT, "co2", 1000.0));
        ParsedSensorMessage message = new ParsedSensorMessage(device, envData, now);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
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
        List<SensorData> newEnvData = List.of(new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 26.0), // 기존 값 덮어쓰기
                new SensorData(MeasurementCategory.ENVIRONMENT, "humidity", 50.0)     // 새로운 값 추가
        );
        ParsedSensorMessage message = new ParsedSensorMessage(device, newEnvData, now);

        EnvironmentContext existingContext = new EnvironmentContext(
                roomId,
                List.of(
                        new EnvironmentContext.MetricInfo("temperature", 22.0, "old-dev-eui", now.minusSeconds(60)),
                        new EnvironmentContext.MetricInfo("co2", 400.0, "other-dev-eui", now.minusSeconds(60))
                ),
                now.minusSeconds(60)
        );

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("env:context:" + roomId)).willReturn(existingContext);

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        verify(valueOperations).set(eq("env:context:" + roomId), contextCaptor.capture());

        EnvironmentContext mergedContext = contextCaptor.getValue();
        assertThat(mergedContext.metrics()).hasSize(3);

        assertThat(mergedContext.metrics()).anyMatch(m -> m.metric().equals("temperature") && m.value().equals(26.0) && m.devEui().equals("devEui1234567890"));
        assertThat(mergedContext.metrics()).anyMatch(m -> m.metric().equals("co2") && m.value().equals(400.0));
        assertThat(mergedContext.metrics()).anyMatch(m -> m.metric().equals("humidity") && m.value().equals(50.0));
    }

    @Test
    @DisplayName("Redis 조회 중 예외 발생 시 신규로 덮어씀")
    void updateContext_Exception() {
        int roomId = 101;
        List<SensorData> envData = List.of(
                new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.0)
        );
        ParsedSensorMessage message = new ParsedSensorMessage(device, envData, now);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("env:context:" + roomId)).willThrow(new RedisConnectionFailureException("Connection refused"));

        Optional<EnvironmentContext> result = service.updateContext(message, roomId);

        assertThat(result).isPresent();
        verify(valueOperations).set(eq("env:context:" + roomId), any(EnvironmentContext.class));
    }
}