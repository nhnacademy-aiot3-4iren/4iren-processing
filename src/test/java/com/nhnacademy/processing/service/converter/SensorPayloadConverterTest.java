package com.nhnacademy.processing.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.exception.SensorPayloadParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorPayloadConverterTest {

    private SensorPayloadConverter converter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Instant 처리를 위한 모듈 등록
        converter = new SensorPayloadConverter(objectMapper);
    }

    @Test
    @DisplayName("정상적인 JSON 페이로드를 파싱하여 모든 데이터를 추출한다")
    void convert_Success_FullData() {
        String json = """
                {
                  "time": "2026-09-02T16:23:37Z",
                  "deviceInfo": {
                    "applicationId": "app1",
                    "applicationName": "appName1",
                    "deviceProfileId": "prof1",
                    "deviceName": "dev1",
                    "devEui": "1234567890abcdef",
                    "tags": {
                      "location": "회의실",
                      "point": "천장"
                    }
                  },
                  "object": {
                    "temperature": 25.4,
                    "battery": 95.5,
                    "unknown_field": 123
                  },
                  "rxInfo": [
                    {
                      "rssi": -50,
                      "snr": 5.5
                    }
                  ]
                }
                """;

        ParsedSensorMessage message = converter.convert(json);

        // 1. DeviceIdentity 검증
        assertThat(message.measuredAt()).isEqualTo(Instant.parse("2026-09-02T16:23:37Z"));
        assertThat(message.device().devEui()).isEqualTo("1234567890abcdef");
        assertThat(message.device().location()).isEqualTo("회의실");
        assertThat(message.device().point()).isEqualTo("천장");
        assertThat(message.device().applicationId()).isEqualTo("app1");

        // 2. SensorData 검증 (unknown_field는 무시되어야 함)
        List<SensorData> dataList = message.sensorDataList();
        assertThat(dataList).hasSize(4); // temperature, battery, rssi, snr

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.ENVIRONMENT);
            assertThat(data.measurement()).isEqualTo("temperature");
            assertThat(data.value()).isEqualTo(25.4);
        });

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.DEVICE_HEALTH);
            assertThat(data.measurement()).isEqualTo("battery");
            assertThat(data.value()).isEqualTo(95.5);
        });

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.NETWORK_QUALITY);
            assertThat(data.measurement()).isEqualTo("rssi");
            assertThat(data.value()).isEqualTo(-50.0);
        });
    }

    @Test
    @DisplayName("잘못된 JSON 형식일 경우 SensorPayloadParseException을 던진다")
    void convert_InvalidJson_ThrowsException() {
        String invalidJson = "{ invalid_json: ";

        assertThatThrownBy(() -> converter.convert(invalidJson))
                .isInstanceOf(SensorPayloadParseException.class)
                .hasMessageContaining("ChirpStack payload 파싱 실패");
    }

    @Test
    @DisplayName("deviceInfo 객체가 누락된 경우 예외를 던진다")
    void convert_MissingDeviceInfo_ThrowsException() {
        String json = """
                {
                  "time": "2026-09-02T12:00:00Z",
                  "object": { "temperature": 25.0 }
                }
                """;

        assertThatThrownBy(() -> converter.convert(json))
                .isInstanceOf(SensorPayloadParseException.class)
                .hasMessage("deviceInfo 또는 devEui 누락");
    }

    @Test
    @DisplayName("devEui가 누락된 경우 예외를 던진다")
    void convert_MissingDevEui_ThrowsException() {
        String json = """
                {
                  "deviceInfo": {
                    "applicationId": "app-1"
                  }
                }
                """;

        assertThatThrownBy(() -> converter.convert(json))
                .isInstanceOf(SensorPayloadParseException.class)
                .hasMessage("deviceInfo 또는 devEui 누락");
    }

    @Test
    @DisplayName("tags, object, rxInfo가 없어도 예외 없이 빈 데이터로 안전하게 파싱한다")
    void convert_NullTagsAndObjects_ParsesSafely() {
        String json = """
                {
                  "deviceInfo": {
                    "devEui": "safeDevice"
                  }
                }
                """;

        ParsedSensorMessage message = converter.convert(json);

        assertThat(message.device().devEui()).isEqualTo("safeDevice");
        assertThat(message.device().location()).isNull();
        assertThat(message.device().point()).isNull();
        assertThat(message.sensorDataList()).isEmpty();
    }

    @Test
    @DisplayName("rxInfo 배열 내에 snr 값이 하나도 없으면 네트워크 품질 데이터를 생략한다")
    void convert_RxInfoWithoutSnr_SkipsNetworkQuality() {
        String json = """
                {
                  "deviceInfo": { "devEui": "devEuiTest" },
                  "rxInfo": [
                    {
                      "rssi": -108
                    }
                  ]
                }
                """;

        ParsedSensorMessage message = converter.convert(json);
        assertThat(message.sensorDataList()).isEmpty(); // snr이 없어 rssi도 함께 추출하지 않음
    }

    @Test
    @DisplayName("다양한 데이터 타입(Boolean, String, Null, Object) 변환을 정확히 처리한다")
    void convert_TypeConversion_AllCases() {
        String json = """
                {
                  "deviceInfo": { "devEui": "typeTest" },
                  "object": {
                    "co2": "1000.5",
                    "humidity": "invalid_string",
                    "door": true,
                    "pressure": false,
                    "tvoc": null,
                    "illumination": {"nested": "object"}
                  }
                }
                """;

        ParsedSensorMessage message = converter.convert(json);
        List<SensorData> dataList = message.sensorDataList();

        // String -> Double 파싱 성공
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("co2");
            assertThat(data.value()).isEqualTo(1000.5);
        });

        // String -> Double 파싱 실패 (NumberFormatException catch) -> 0.0
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("humidity");
            assertThat(data.value()).isEqualTo(0.0);
        });

        // Boolean true -> 1.0
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("door");
            assertThat(data.value()).isEqualTo(1.0);
        });

        // Boolean false -> 0.0
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("pressure");
            assertThat(data.value()).isEqualTo(0.0);
        });

        // Null -> 0.0
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("tvoc");
            assertThat(data.value()).isEqualTo(0.0);
        });

        // Object 등 지원하지 않는 타입 (default 분기) -> 0.0
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("illumination");
            assertThat(data.value()).isEqualTo(0.0);
        });
    }
}