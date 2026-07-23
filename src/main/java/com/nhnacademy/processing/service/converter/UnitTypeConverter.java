package com.nhnacademy.processing.service.converter;

import com.nhnacademy.processing.domain.UnitType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UnitTypeConverter implements AttributeConverter<UnitType, String> {
    @Override
    public String convertToDatabaseColumn(UnitType attribute) {
        if(attribute == null) {
            return UnitType.NONE.getSymbol();
        }
        return attribute.getSymbol();
    }

    @Override
    public UnitType convertToEntityAttribute(String dbData) {
        return UnitType.fromSymbol(dbData);
    }
}
