package com.nhnacademy.processing.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public enum UnitType {

    PPM("ppm"),
    PERCENT("%"),
    LUX("lux"),
    PA("Pa"),
    CELSIUS("°C"),
    PPB("ppb"),
    NONE("");

    private final String symbol;

    @JsonValue
    public String getSymbol() {
        return symbol;
    }

    @JsonCreator
    public static UnitType fromSymbol(String symbol) {
        if(symbol == null || symbol.isBlank()) {
            return NONE;
        }
        return Arrays.stream(UnitType.values())
                .filter(unit -> unit.getSymbol().equalsIgnoreCase(symbol.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 단위: " + symbol));
    }
}
