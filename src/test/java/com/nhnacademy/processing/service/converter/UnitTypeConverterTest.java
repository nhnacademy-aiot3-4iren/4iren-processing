package com.nhnacademy.processing.service.converter;

import com.nhnacademy.processing.domain.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTypeConverterTest {

    private UnitTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UnitTypeConverter();
    }

    @Test
    @DisplayName("DB 저장 시 Enum 값을 Symbol 문자열로 변환")
    void convertToColumn() {
        String dbDataNull = converter.convertToDatabaseColumn(null);
        assertThat(dbDataNull).isEqualTo(UnitType.NONE.getSymbol());

        String dataDataPercent = converter.convertToDatabaseColumn(UnitType.PERCENT);
        assertThat(dataDataPercent).isEqualTo(UnitType.PERCENT.getSymbol());
    }

    @Test
    @DisplayName("DB에서 읽어올 때 Symbol 문자열을 UnitType Enum으로 변환")
    void convertToEntity() {
        UnitType unitType = converter.convertToEntityAttribute("ppm");
        assertThat(unitType).isEqualTo(UnitType.PPM);
    }
}
