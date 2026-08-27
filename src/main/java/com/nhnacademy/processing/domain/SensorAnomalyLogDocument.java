package com.nhnacademy.processing.domain;

import com.nhnacademy.processing.dto.rule.ValidationStatus;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(indexName = "4iren-sensor-anomaly-log")
public class SensorAnomalyLogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String measurement;

    @Field(type = FieldType.Double)
    private Double value;

    @Field(type = FieldType.Keyword)
    private String devEui;

    @Field(type = FieldType.Keyword)
    private Integer roomId;

    @Field(type = FieldType.Keyword)
    private ValidationStatus status;

    @Field(type = FieldType.Date)
    private Instant detectedAt;

    public SensorAnomalyLogDocument(String measurement, Double value, String devEui, Integer roomId, ValidationStatus status, Instant detectedAt) {
        this.measurement = measurement;
        this.value = value;
        this.devEui = devEui;
        this.roomId = roomId;
        this.status = status;
        this.detectedAt = detectedAt;
    }
}
