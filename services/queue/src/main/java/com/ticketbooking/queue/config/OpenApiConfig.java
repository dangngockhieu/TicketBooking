package com.ticketbooking.queue.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI tại {@code /swagger-ui.html}, spec JSON tại {@code /v3/api-docs}.
 * Chỉ liệt kê các REST endpoint (status/admin config/internal) — kênh
 * WebSocket STOMP (join/heartbeat) không thuộc phạm vi OpenAPI (xem
 * docs/virtual-waiting-room.md).
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    @Bean
    public OpenAPI queueServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Queue Service API")
                        .description("Phòng chờ ảo — status/config REST; join/heartbeat qua WebSocket STOMP (không có trong spec này).")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
