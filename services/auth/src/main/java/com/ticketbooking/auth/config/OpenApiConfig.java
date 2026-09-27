package com.ticketbooking.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI tại {@code /swagger-ui.html}, spec JSON tại {@code /v3/api-docs}
 * (xem docs/development-plan.md GĐ7 mục 3 "Hoàn thiện tài liệu"). Đăng ký
 * scheme {@code bearer-jwt} để nút "Authorize" trên UI đính kèm header
 * {@code Authorization: Bearer <token>} cho các route yêu cầu xác thực.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API")
                        .description("Đăng ký/đăng nhập, JWT (RS256), JWKS, quản trị tài khoản Organizer.")
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
