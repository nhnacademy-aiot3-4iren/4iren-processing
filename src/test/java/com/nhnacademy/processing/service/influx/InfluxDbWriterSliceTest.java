package com.nhnacademy.processing.service.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.nhnacademy.processing.dto.influx.SensorInfluxPointDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class InfluxDbWriterSliceTest {

    private static final String ORG = "4iren-test";
    private static final String BUCKET = "sensor-data-test";
    private static final String TOKEN = "test-token-1234567890";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin1234";
    private static final String MEASUREMENT = "sensor_telemetry";

    @Container
    static GenericContainer<?> influx = new GenericContainer<>(DockerImageName.parse("influxdb:2.7"))
            .withExposedPorts(8086)
            .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
            .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", USERNAME)
            .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", PASSWORD)
            .withEnv("DOCKER_INFLUXDB_INIT_ORG", ORG)
            .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
            .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", TOKEN)
            .waitingFor(Wait.forHttp("/health").forPort(8086).withStartupTimeout(Duration.ofSeconds(60)));

    private InfluxDBClient client;
    private InfluxDbWriter writer;

    @BeforeEach
    void setUp() {
        String url = "http://" + influx.getHost() + ":" + influx.getMappedPort(8086);
        client = InfluxDBClientFactory.create(url, TOKEN.toCharArray(), ORG, BUCKET);
        writer = new InfluxDbWriter(client);
        writer.init();
    }

    @AfterEach
    void tearDown() {
        writer.shutdown();
        client.close();
    }

    private List<FluxTable> awaitQuery(String flux) {
        QueryApi queryApi = client.getQueryApi();
        AtomicReference<List<FluxTable>> resultHolder = new AtomicReference<>(List.of());

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    List<FluxTable> tables = queryApi.query(flux);
                    assertThat(tables).isNotEmpty();
                    resultHolder.set(tables);
                });

        return resultHolder.get();
    }

    private String randomDevEui() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Test
    @DisplayName("단건 환경 데이터를 비동기로 작성 -> InfluxDB에 sensor_telemetry measurement로 정상 적재되어 조회됨")
    void writerAsync_Success() {
        String devEui = randomDevEui();
        SensorInfluxPointDto dto = new SensorInfluxPointDto("temperature", 25.0, Instant.now(), "applicationId", devEui, "deviceName", "location", 101);
        writer.writeAsync(dto);

        List<FluxTable> tables = awaitQuery("""
                from(bucket: "%s")
                    |> range(start: -1h)
                    |> filter(fn: (r) => r._measurement == "%s" and r.metric == "temperature" and r.dev_eui == "%s")
                """.formatted(BUCKET, MEASUREMENT, devEui));

        assertThat(tables).isNotEmpty();
        FluxRecord record = tables.getFirst().getRecords().getFirst();
        assertThat(record.getMeasurement()).isEqualTo(MEASUREMENT);
        assertThat(record.getValueByKey("metric")).isEqualTo("temperature");
        assertThat(record.getValueByKey("_value")).isEqualTo(25.0);
        assertThat(record.getValueByKey("room_id")).isEqualTo("101");
        assertThat(record.getValueByKey("device_name")).isEqualTo("deviceName");
    }

    @Test
    @DisplayName("동일 센서의 연속 데이터 수신 시 모든 포인트가 누락 없이 저장")
    void writeAsync_MultiplePoints_Success() {
        String devEui = randomDevEui();
        List<SensorInfluxPointDto> dtos = List.of(
                new SensorInfluxPointDto("temperature", 25.0, Instant.now(), "applicationId", devEui, "deviceName", "location", 101),
                new SensorInfluxPointDto("co2", 900.0, Instant.now(), "applicationId", devEui, "deviceName", "location", 101)
        );

        dtos.forEach(writer::writeAsync);

        List<FluxTable> tables = awaitQuery("""
            from(bucket: "%s")
              |> range(start: -1h)
              |> filter(fn: (r) => r._measurement == "%s" and r.dev_eui == "%s")
            """.formatted(BUCKET, MEASUREMENT, devEui));

        long recordCount = tables.stream().mapToLong(t -> t.getRecords().size()).sum();
        assertThat(recordCount).isEqualTo(2);
    }

    @Test
    @DisplayName("설정한 태그 메타데이터(metric, application_id, device_name 등)가 정확히 매핑됨")
    void writeAsync_TagVerification() {
        String devEui = randomDevEui();
        SensorInfluxPointDto dto = new SensorInfluxPointDto("co2", 900.0, Instant.now(), "applicationId", devEui, "deviceName", "location", 101);

        writer.writeAsync(dto);

        List<FluxTable> tables = awaitQuery("""
            from(bucket: "%s")
              |> range(start: -1h)
              |> filter(fn: (r) => r._measurement == "%s" and r.metric == "co2" and r.dev_eui == "%s")
            """.formatted(BUCKET, MEASUREMENT, devEui));

        FluxRecord record = tables.getFirst().getRecords().getFirst();
        assertThat(record.getMeasurement()).isEqualTo(MEASUREMENT);
        assertThat(record.getValueByKey("metric")).isEqualTo("co2");
        assertThat(record.getValueByKey("application_id")).isEqualTo("applicationId");
        assertThat(record.getValueByKey("device_name")).isEqualTo("deviceName");
        assertThat(record.getValueByKey("room_id")).isEqualTo("101");
    }
}