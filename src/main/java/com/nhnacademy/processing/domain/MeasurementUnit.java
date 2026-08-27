package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "measurement_units")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "measurement_unit_id")
    private Long id;

    @Column(length = 64, nullable = false)
    private String ucumCode;

    @Column(length = 50, nullable = false)
    private String displayName;

    @Column(length = 32, nullable = false)
    private String symbol;

    @Column(nullable = false)
    private Boolean enabled;

    public MeasurementUnit(Long id, String ucumCode, String displayName, String symbol) {
        this.id = id;
        this.ucumCode = ucumCode;
        this.displayName = displayName;
        this.symbol = symbol;
        this.enabled = true;
    }

    public MeasurementUnit(String ucumCode, String displayName, String symbol) {
        this.ucumCode = ucumCode;
        this.displayName = displayName;
        this.symbol = symbol;
        this.enabled = true;
    }

    public void enable() {
        this.enabled = true;
    }
    public void disable() {
        this.enabled = false;
    }
}
