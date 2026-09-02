package com.nhnacademy.processing.service.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import com.influxdb.client.write.Point;
import com.influxdb.client.write.events.WriteErrorEvent;
import com.nhnacademy.processing.dto.influx.SensorInfluxPointDto;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfluxDbWriterTest {

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private WriteApi writeApi;

    @InjectMocks
    private InfluxDbWriter writer;

    private SensorData sensorData;
    private ParsedSensorMessage parsedMessage;

    @BeforeEach
    void setUp() {
        // 공통 도메인 객체 세팅
        DeviceIdentity device = new DeviceIdentity(
                "app-1", "appName", "prof-1", "devName",
                "devEui123", 101, "loc", "point"
        );
        sensorData = new SensorData(MeasurementCategory.ENVIRONMENT, "temperature", 25.5);
        parsedMessage = new ParsedSensorMessage(device, List.of(sensorData), Instant.now());
    }

    @Test
    @DisplayName("초기화 시 WriteApi 생성 및 이벤트 리스너가 등록된다")
    void init_Success() {
        when(influxDBClient.makeWriteApi(any(WriteOptions.class))).thenReturn(writeApi);

        writer.init();

        verify(influxDBClient, times(1)).makeWriteApi(any(WriteOptions.class));
        verify(writeApi, times(1)).listenEvents(eq(WriteErrorEvent.class), any());
    }

    @Test
    @DisplayName("SensorData와 Message를 InfluxDB Point로 변환하여 비동기로 기록한다 (roomId 포함)")
    void writeAsync_DomainObjects_WithRoomId() {
        // Given: init() 수동 호출로 writeApi 연결 모킹
        when(influxDBClient.makeWriteApi(any(WriteOptions.class))).thenReturn(writeApi);
        writer.init();

        // When: 메서드 호출
        writer.writeAsync(sensorData, parsedMessage, 101);

        // Then: ArgumentCaptor를 사용하여 변환된 Point 검증
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
        verify(writeApi, times(1)).writePoint(pointCaptor.capture());

        Point capturedPoint = pointCaptor.getValue();
        assertThat(capturedPoint).isNotNull();
        String lineProtocol = capturedPoint.toLineProtocol();

        // measurement 이름 검증
        assertThat(lineProtocol).startsWith("sensor_telemetry");

        // 태그 검증 (toPoint 매핑 로직 완벽 커버리지 체크)
        assertThat(lineProtocol).contains("metric=temperature");
        assertThat(lineProtocol).contains("application_id=app-1");
        assertThat(lineProtocol).contains("dev_eui=devEui123");
        assertThat(lineProtocol).contains("device_name=devName");
        assertThat(lineProtocol).contains("location=loc");
        assertThat(lineProtocol).contains("room_id=101"); // roomId가 null이 아니므로 태그 포함

        // 필드(Value) 검증
        assertThat(lineProtocol).contains("value=25.5");
    }

    @Test
    @DisplayName("roomId가 null일 경우 room_id 태그를 제외하고 Point를 생성 및 기록한다")
    void writeAsync_DomainObjects_NullRoomId() {
        // Given
        when(influxDBClient.makeWriteApi(any(WriteOptions.class))).thenReturn(writeApi);
        writer.init();

        // When
        writer.writeAsync(sensorData, parsedMessage, null);

        // Then
        ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
        verify(writeApi, times(1)).writePoint(pointCaptor.capture());

        String lineProtocol = pointCaptor.getValue().toLineProtocol();
        assertThat(lineProtocol).doesNotContain("room_id="); // roomId가 null이므로 태그 생략 확인
    }

    @Test
    @DisplayName("WriteApi 호출 중 예외가 발생하면 에러 로그를 남기고 정상 종료된다 (예외 전파 안함)")
    void writeAsync_ExceptionCaught() {
        // Given
        when(influxDBClient.makeWriteApi(any(WriteOptions.class))).thenReturn(writeApi);
        writer.init();

        // 강제로 예외 발생 설정
        doThrow(new RuntimeException("InfluxDB Connection Timeout"))
                .when(writeApi).writePoint(any(Point.class));

        SensorInfluxPointDto dto = new SensorInfluxPointDto(
                "temperature", 25.5, Instant.now(), "app-1", "devEui123", "devName", "loc", 101
        );

        // When & Then: 예외가 발생하더라도 catch 블록에서 안전하게 처리되어야 함
        assertThatCode(() -> writer.writeAsync(dto)).doesNotThrowAnyException();
        verify(writeApi, times(1)).writePoint(any(Point.class));
    }

    @Test
    @DisplayName("shutdown 호출 시 writeApi가 null이 아니면 close()를 호출한다")
    void shutdown_WithNonNullWriteApi() {
        // Given
        when(influxDBClient.makeWriteApi(any(WriteOptions.class))).thenReturn(writeApi);
        writer.init();

        // When
        writer.shutdown();

        // Then
        verify(writeApi, times(1)).close();
    }

    @Test
    @DisplayName("shutdown 호출 시 writeApi가 null이면 아무 동작도 하지 않는다")
    void shutdown_WithNullWriteApi() {
        // init()을 호출하지 않아서 writeApi가 초기화(null) 상태인 새로운 writer 인스턴스 생성
        InfluxDbWriter cleanWriter = new InfluxDbWriter(influxDBClient);

        // 아무 예외 없이 조용히 통과해야 함
        assertThatCode(cleanWriter::shutdown).doesNotThrowAnyException();
    }
}