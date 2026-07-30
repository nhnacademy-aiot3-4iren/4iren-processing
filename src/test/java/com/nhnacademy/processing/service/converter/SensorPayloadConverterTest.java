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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorPayloadConverterTest {

    private SensorPayloadConverter converter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        converter = new SensorPayloadConverter(objectMapper);
    }

    @Test
    @DisplayName("정상적인 MQTT JSON 페이로드를 ParsedSensorMessage로 변환")
    void convert_Success() {
        String rawJson = """
                {
                    "deduplicationId":"bc0012ff-a6d6-46a5-a701-119547304cf9",
                    "time":"2026-07-15T06:42:19.894+00:00",
                    "deviceInfo":{
                        "tenantId":"84830715-69bb-4e77-be95-1062c8d47fbf",
                        "tenantName":"ChirpStack",
                        "applicationId":"96b4d719-f23f-40aa-9f94-a0f2d0354342",
                        "applicationName":"광주 캠퍼스",
                        "deviceProfileId":"53fd0d70-6b05-48e3-ad81-ac44aca28b69",
                        "deviceProfileName":"AM103",
                        "deviceName":"AM103-081175",
                        "devEui":"24e124725d081175",
                        "deviceClassEnabled":"CLASS_A",
                        "tags":{
                            "location":"사무실",
                            "point":"업무 공간 안쪽"
                        }
                    },
                    "devAddr":"010a3fca",
                    "adr":true,
                    "dr":2,
                    "fCnt":71935,
                    "fPort":85,
                    "confirmed":false,
                    "data":"AXUBA2fxAARoiwd9EAU=",
                    "object":{
                        "battery":1,
                        "co2":1296,
                        "temperature":24.1,
                        "humidity":69.5,
                        "door":true
                    },
                    "rxInfo":[
                        {
                            "gatewayId":"24e124fffef5dccc",
                            "uplinkId":33357,
                            "gwTime":"2026-07-15T06:42:19.894763+00:00",
                            "nsTime":"2026-07-15T06:38:26.425064464+00:00",
                            "timeSinceGpsEpoch":"1468132957.894s",
                            "rssi":-46,
                            "snr":13,
                            "location":{},
                            "context":"KHlJ5w==",
                            "crcStatus":"CRC_OK"
                        }
                    ],
                    "txInfo":{
                        "frequency":922100000,
                        "modulation":{
                            "lora":{
                                "bandwidth":125000,
                                "spreadingFactor":10,
                                "codeRate":"CR_4_5"
                            }
                        }
                    },
                    "regionConfigId":"kr920"
                }
                """;

        ParsedSensorMessage message = converter.convert(rawJson);

        assertThat(message.measuredAt()).isNotNull();
        assertThat(message.device().devEui()).isEqualTo("24e124725d081175");
        assertThat(message.device().location()).isEqualTo("사무실");

        List<SensorData> dataList = message.sensorDataList();

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.ENVIRONMENT);
            assertThat(data.measurement()).isEqualTo("temperature");
            assertThat(data.value()).isEqualTo(24.1);
        });
        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.ENVIRONMENT);
            assertThat(data.measurement()).isEqualTo("door");
            assertThat(data.value()).isEqualTo(1.0);
        });

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.DEVICE_HEALTH);
            assertThat(data.measurement()).isEqualTo("battery");
            assertThat(data.value()).isEqualTo(1.0);
        });

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.category()).isEqualTo(MeasurementCategory.NETWORK_QUALITY);
            assertThat(data.measurement()).isEqualTo("rssi");
            assertThat(data.value()).isEqualTo(-46);
        });
    }

    @Test
    @DisplayName("잘못된 JSON 형식이면 SensorPayloadParseException 발생")
    void convert_InvalidJson() {
        String invalidJson = "{ invalid_json: ";

        assertThatThrownBy(() -> converter.convert(invalidJson))
                .isInstanceOf(SensorPayloadParseException.class)
                .hasMessageContaining("ChirpStack payload 역직렬화 실패");
    }

    @Test
    @DisplayName("deviceInfo 또는 devEui 누락 시 예외 발생")
    void convert_MissingDevEui() {
        String missingEuiJson = """
                {
                  "time": "2023-10-25T12:00:00Z",
                  "deviceInfo": {
                    "applicationId": "app-1"
                  },
                  "object": { "temperature": 25.0 }
                }
                """;

        assertThatThrownBy(() -> converter.convert(missingEuiJson))
                .isInstanceOf(SensorPayloadParseException.class)
                .hasMessage("deviceInfo 또는 devEui 누락");
    }

    @Test
    @DisplayName("object 항목이 비어있으면 예외 발생")
    void convert_EmptyObject() {
        String emptyObjectJson = """
                {
                  "deviceInfo": {
                    "devEui": "1234567890abcdef"
                  },
                  "object": {}
                }
                """;

        assertThatThrownBy(() -> converter.convert(emptyObjectJson))
                .isInstanceOf(SensorPayloadParseException.class)
                .hasMessageContaining("측정값 없음");
    }

    @Test
    @DisplayName("문자열 타입의 숫자 데이터도 정상적으로 double로 파싱")
    void convert_StringNumberToDouble() {
        String json = """
                {
                  "deviceInfo": { "devEui": "testEui" },
                  "object": {
                    "humidity": "45.5"
                  }
                }
                """;

        ParsedSensorMessage message = converter.convert(json);

        assertThat(message.sensorDataList()).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("humidity");
            assertThat(data.value()).isEqualTo(45.5);
        });
    }
}