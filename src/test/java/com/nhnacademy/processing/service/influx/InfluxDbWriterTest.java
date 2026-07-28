package com.nhnacademy.processing.service.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.write.Point;
import com.nhnacademy.processing.dto.influx.SensorInfluxPointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfluxDbWriterTest {

    @Mock
    private InfluxDBClient client;
    @Mock
    private WriteApi writeApi;

    @InjectMocks
    private InfluxDbWriter writer;

    @BeforeEach
    void setUp(){
        when(client.makeWriteApi(any())).thenReturn(writeApi);
        writer.init();
    }

    @Test
    @DisplayName("센서 데이터 전달 시 InfluxDb Point 생성 및 writePoint 1회 호출")
    void writeAsync_success() {
        SensorInfluxPointDto dto = new SensorInfluxPointDto(
                "temperature", 25.0, Instant.now(),
                "applicationId", "devEui", "deviceName", 1
        );

        writer.writeAsync(dto);

        verify(writeApi, times(1)).writePoint(any(Point.class));
    }

    @Test
    @DisplayName("예외 발생 시 중단되지 않음")
    void writeAsync_fail() {
        doThrow(new RuntimeException("influx down")).when(writeApi).writePoint(any());

        SensorInfluxPointDto dto = new SensorInfluxPointDto(
                "temperature", 25.0, Instant.now(),
                "applicationId", "devEui", "deviceName", 1
        );

        assertThatCode(() -> writer.writeAsync(dto))
                .doesNotThrowAnyException();
    }
}
