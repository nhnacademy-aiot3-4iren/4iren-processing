package com.nhnacademy.processing;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import java.time.Instant;

public class InfluxTest {
    public static void main(String[] args) {
        String url = "http://localhost:8086";
        String token = "mQcJQbbBB4RZoYtFstEqJTP-I_VfaKI6ITkkgHjIuuIdOfw2EVPp2eO9mtKQOWMYkX2xsbNPyFGT0c2cqKI2IA==";
        String org = "iot-lab";
        String bucket = "test";
        try (InfluxDBClient client = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket)) {
            WriteApiBlocking writeApi = client.getWriteApiBlocking();
            Point point = new Point("test_java").addField("value", 2.0).time(Instant.now(), WritePrecision.MS);
            writeApi.writePoint(point);
            System.out.println("Success!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
