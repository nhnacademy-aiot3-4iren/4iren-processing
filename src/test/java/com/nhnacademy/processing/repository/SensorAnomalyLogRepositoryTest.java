package com.nhnacademy.processing.repository;

import com.nhnacademy.processing.domain.SensorAnomalyLogDocument;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataElasticsearchTest
class SensorAnomalyLogRepositoryTest {

    @Container
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
                    .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void esProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @Autowired
    private SensorAnomalyLogRepository repository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("문서를 저장하면 ID 자동 생성")
    void saveDocument_GenerateId() {
        SensorAnomalyLogDocument document = new SensorAnomalyLogDocument("co2", 5000.0, "123456789abcedfg", 12, ValidationStatus.OUT_OF_RANGE, Instant.now());
        SensorAnomalyLogDocument saved = repository.save(document);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("저자한 문서의 필드가 변경 없이 조회됨")
    void saveAndFindById() {
        SensorAnomalyLogDocument document = new SensorAnomalyLogDocument("temperature", 40.0, "123456789abcdefg", 13, ValidationStatus.OUT_OF_RANGE, Instant.now());
        SensorAnomalyLogDocument saved = repository.save(document);

        elasticsearchOperations.indexOps(SensorAnomalyLogDocument.class).refresh();

        Optional<SensorAnomalyLogDocument> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getMeasurement()).isEqualTo("temperature");
        assertThat(found.get().getValue()).isEqualTo(40.0);
        assertThat(found.get().getDevEui()).isEqualTo("123456789abcdefg");
        assertThat(found.get().getRoomId()).isEqualTo(13);
        assertThat(found.get().getStatus()).isEqualTo(ValidationStatus.OUT_OF_RANGE);
    }

    @Test
    @DisplayName("여러 건 저장 후 전체 조회 가능")
    void saveAlAndFindAll() {
        repository.saveAll(List.of(
                new SensorAnomalyLogDocument("co2", 5000.0, "123456789abcdefg", 13, ValidationStatus.OUT_OF_RANGE, Instant.now()),
                new SensorAnomalyLogDocument("temperature", 40.0, "0123456789abcedf", 14, ValidationStatus.OUT_OF_RANGE, Instant.now())
        ));
        elasticsearchOperations.indexOps(SensorAnomalyLogDocument.class).refresh();

        List<SensorAnomalyLogDocument> all = StreamSupport.stream(repository.findAll().spliterator(), false).toList();

        assertThat(all).hasSize(2);
    }
}
