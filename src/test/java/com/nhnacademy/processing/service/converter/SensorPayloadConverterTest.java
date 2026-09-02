package com.nhnacademy.processing.service.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
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
    @DisplayName("문자열로 된 숫자를 정상적으로 double로 파싱한다")
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

    @Test
    @DisplayName("Double 변환 불가능한 문자열 및 Null은 0.0으로 처리한다")
    void convert_InvalidStringNumberToDouble_ReturnsZero() {
        String json = """
                {
                  "time": "2023-10-25T12:00:00Z",
                  "deviceInfo": { "devEui": "testEui" },
                  "object": {
                    "temperature": "NotANumber",
                    "co2": null
                  }
                }
                """;
        ParsedSensorMessage message = converter.convert(json);

        List<SensorData> dataList = message.sensorDataList();

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("temperature");
            assertThat(data.value()).isEqualTo(0.0);
        });

        assertThat(dataList).anySatisfy(data -> {
            assertThat(data.measurement()).isEqualTo("co2");
            assertThat(data.value()).isEqualTo(0.0);
        });
    }

    @Test
    @DisplayName("deviceInfo에 devEui가 없으면 예외를 발생시킨다")
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
}