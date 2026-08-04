package com.nhnacademy.processing.config.integration;

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
 *   -> [Splitter] sensorDataList()를 개별 SensorData로 분할
 *   -> [Router]   category / 검증 결과에 따라 normal/anomaly로 분기
 *       - NETWORK_QUALITY, DEVICE_HEALTH : 검증 없이 항상 normal (기존 process() switch와 동일)
 *       - ENVIRONMENT                    : SensorValidator.validate() 결과가 VALID면 normal, 아니면 anomaly
 *   -> normal  : InfluxDbWriter.writeAsync()
 *   -> anomaly : SensorAnomalyLogService.log() (ENVIRONMENT 중 OUT_OF_RANGE/NO_RULE_DEFINED만 여기로 온다)
 */
@Configuration
public class SensorDbSubFlowConfig {

    private static final String NORMAL = "normal";
    private static final String ANOMALY = "anomaly";

    @Bean
    public IntegrationFlow sensorDbSubFlow(
            SensorValidator sensorValidator,
            InfluxDbWriter influxDbWriter,
            SensorAnomalyLogService anomalyLogService) {

        return IntegrationFlow.from("sensorPubSubChannel")
                // 1. Splitter: ParsedSensorMessage -> 개별 SensorData
                .split(ParsedSensorMessage.class, ParsedSensorMessage::sensorDataList)

                // 2. Router: category / 검증 결과에 따라 normal, anomaly로 분기
                .<SensorData, String>route(data -> {
                            if (data.category() == MeasurementCategory.ENVIRONMENT) {
                                return sensorValidator.validate(data) == ValidationStatus.VALID ? NORMAL : ANOMALY;
                            }
                            return NORMAL; // NETWORK_QUALITY, DEVICE_HEALTH
                        },
                        mapping -> mapping
                                .subFlowMapping(NORMAL, sub -> sub.handle(SensorData.class, (data, headers) -> {
                                    ParsedSensorMessage parsed = headers.get(SensorMessageHeaders.PARSED_MESSAGE, ParsedSensorMessage.class);
                                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                                    influxDbWriter.writeAsync(data, parsed, roomId);
                                    return null; // 종착점, 리턴 메시지 없음
                                }))
                                .subFlowMapping(ANOMALY, sub -> sub.handle(SensorData.class, (data, headers) -> {
                                    ParsedSensorMessage parsed = headers.get(SensorMessageHeaders.PARSED_MESSAGE, ParsedSensorMessage.class);
                                    Integer roomId = headers.get(SensorMessageHeaders.ROOM_ID, Integer.class);
                                    ValidationStatus status = sensorValidator.validate(data);
                                    anomalyLogService.log(data, parsed.device().devEui(), roomId, status, parsed.measuredAt());
                                    return null;
                                }))
                )
                .get();
    }
}
