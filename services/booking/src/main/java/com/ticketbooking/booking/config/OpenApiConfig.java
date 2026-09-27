package com.ticketbooking.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI tại {@code /swagger-ui.html}, spec JSON tại {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    @Bean
    public OpenAPI bookingServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Booking Service API")
                        .description("Đặt vé, giữ chỗ atomic, check-in QR, báo cáo doanh thu/tỷ lệ lấp đầy.")
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
