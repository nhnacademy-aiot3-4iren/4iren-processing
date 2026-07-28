package com.nhnacademy.processing.service.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteOptions;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.client.write.events.WriteErrorEvent;
import com.nhnacademy.processing.dto.influx.SensorInfluxPointDto;
import com.nhnacademy.processing.dto.parse.DeviceIdentity;
import com.nhnacademy.processing.dto.parse.ParsedSensorMessage;
import com.nhnacademy.processing.dto.parse.SensorData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InfluxDbWriter {

    private final InfluxDBClient influxDBClient;
    private WriteApi writeApi;

    @PostConstruct
    public void init() {
        WriteOptions writeOptions = WriteOptions.builder().batchSize(1000).flushInterval(1000).build();
        writeApi = influxDBClient.makeWriteApi(writeOptions);


        writeApi.listenEvents(WriteErrorEvent.class, event -> log.error("InfluxDb 배치 쓰기 실패", event.getThrowable()));
    }

    public void writeAsync(SensorData data, ParsedSensorMessage message, int roomId) {
        SensorInfluxPointDto dto = toInfluxDto(data, message, roomId);

        writeAsync(dto);
    }

    public void writeAsync(SensorInfluxPointDto dto) {
        try {
            writeApi.writePoint(toPoint(dto));
        } catch (Exception e) {
            log.error("InfluxDB Point 생성 실패: measurement({}), devEui({})", dto.measurement(), dto.devEui(), e);
        }
    }

    private SensorInfluxPointDto toInfluxDto(SensorData data, ParsedSensorMessage message, int roomId) {
        DeviceIdentity device = message.device();
        return new SensorInfluxPointDto(data.measurement(), data.value(),
                message.measuredAt(),
                device.applicationId(), device.devEui(), device.deviceName(),
                roomId
        );
    }

    private Point toPoint(SensorInfluxPointDto dto) {
        return new Point(dto.measurement())
                .time(dto.measuredAt(), WritePrecision.MS)
                .addTag("application_id", dto.applicationId())
                .addTag("dev_eui", dto.devEui())
                .addTag("device_name", dto.deviceName())
                .addTag("room_id", String.valueOf(dto.roomId()))
                .addField("value", dto.value());
    }

    @PreDestroy
    public void shutdown() {
        if(writeApi != null) {
            writeApi.close();
        }
    }
}
