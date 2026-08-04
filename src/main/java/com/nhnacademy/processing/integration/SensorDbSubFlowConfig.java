package com.nhnacademy.processing.integration;

import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import com.nhnacademy.processing.dto.rule.MeasurementCategory;
import com.nhnacademy.processing.dto.rule.ValidationStatus;
import com.nhnacademy.processing.service.es.SensorAnomalyLogService;
import com.nhnacademy.processing.service.influx.InfluxDbWriter;
import com.nhnacademy.processing.service.validation.SensorValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

/**
 * DB Sub-flow
 *
 * sensorPubSubChannel(ParsedSensorMessage)을 구독해서
 *   -> [Splitter] sensorDataList()를 개별 SensorData로 분할하면서, ENVIRONMENT 항목은
 *                 이 시점에 SensorValidator.validate()를 호출해 결과를 같이 묶음
 *                 (ValidatedSensorData). NETWORK_QUALITY/DEVICE_HEALTH는 검증 대상이 아니므로 status=null.
 *   -> [Router]   status가 null이거나 VALID면 normal, 그 외(OUT_OF_RANGE/NO_RULE_DEFINED)면 anomaly
 *   -> normal  : InfluxDbWriter.writeAsync()
 *   -> anomaly : SensorAnomalyLogService.log() — Router가 이미 계산해둔 status를 그대로 재사용(재검증 없음)
 */
@Configuration
public class SensorDbSubFlowConfig {

    private static final String NORMAL = "normal";
    private static final String ANOMALY = "anomaly";

    private record ValidatedSensorData(SensorData data, ValidationStatus status) {}

    @Bean
    public IntegrationFlow sensorDbSubFlow(SensorValidator sensorValidator,
                                           InfluxDbWriter influxDbWriter,
                                           SensorAnomalyLogService anomalyLogService) {

        return IntegrationFlow.from("sensorPubSubChannel")
                // 1. Splitter: ParsedSensorMessage -> ValidatedSensorData 리스트로 분할
                .split(ParsedSensorMessage.class, parsed -> parsed.sensorDataList().stream()
                        .map(data -> new ValidatedSensorData(
                                data,
                                data.category() == MeasurementCategory.ENVIRONMENT
                                        ? sensorValidator.validate(data)
                                        : null))
                        .toList())

                // 2. Router: status가 null(검증 대상 아님) 또는 VALID면 normal, 아니면 anomaly
                .<ValidatedSensorData, String>route(
                        vd -> vd.status() == null || vd.status() == ValidationStatus.VALID ? NORMAL : ANOMALY,
                        mapping -> mapping
                                .subFlowMapping(NORMAL, sub -> sub.handle(ValidatedSensorData.class, (vd, headers) -> {
                                    ParsedSensorMessage parsed = headers.get(SensorMessageHeaders.PARSED_MESSAGE, ParsedSensorMessage.class);
                                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                                    influxDbWriter.writeAsync(vd.data(), parsed, roomId);
                                    return null; // 종착점, 리턴 메시지 없음
                                }))
                                .subFlowMapping(ANOMALY, sub -> sub.handle(ValidatedSensorData.class, (vd, headers) -> {
                                    ParsedSensorMessage parsed = headers.get(SensorMessageHeaders.PARSED_MESSAGE, ParsedSensorMessage.class);
                                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                                    anomalyLogService.log(vd.data(), parsed.device().devEui(), roomId, vd.status(), parsed.measuredAt());
                                    return null;
                                }))
                )
                .get();
    }
}
