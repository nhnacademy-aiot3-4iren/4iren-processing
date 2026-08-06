package com.nhnacademy.processing.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "metric_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MetricType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_type_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_unit_id", nullable = false)
    private MeasurementUnit unit;

    @Column(name = "metric_code", length = 50, nullable = false)
    private String code;

    @Column(length = 50, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_kind", length = 20, nullable = false)
    private MetricKind kind;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MetricTypeStatus status;

    @Column
    private String description;

    public MetricType(MeasurementUnit unit, String code, String displayName, MetricKind kind, MetricTypeStatus status, String description) {
        this.unit = unit;
        this.code = code;
        this.displayName = displayName;
        this.kind = kind;
        this.status = status;
        this.description = description;
    }
}
