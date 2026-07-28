package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorAnomalyLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SensorAnomalyLogRepository extends ElasticsearchRepository<SensorAnomalyLogDocument, String> {
}
