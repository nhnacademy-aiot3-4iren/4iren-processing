package com.nhnacademy.processing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI processingOpenApi() {

        String userIdHeader = "X-User-Id";
        String userRoleHeader = "X-User-Role";

        return new OpenAPI()
                .info(new Info()
                        .title("4iren Processing API")
                        .description("센서 데이터 수집 및 MQTT 브로커 관리 서비스 API 명세서")
                        .version("v1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("UserIdAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(userIdHeader)
                                .description("사용자 고유 ID (양의 정수)"))
                        .addSecuritySchemes("UserRoleAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(userRoleHeader)
                                .description("사용자 권한 (ADMIN, NORMAL)")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("UserIdAuth")
                        .addList("UserRoleAuth"));
    }
}
