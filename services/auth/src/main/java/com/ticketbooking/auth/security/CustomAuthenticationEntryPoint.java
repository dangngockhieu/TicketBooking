package com.ticketbooking.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());

        String message = "Bạn cần đăng nhập (hoặc đính kèm Bearer Token) để truy cập tài nguyên này.";
        if (authException instanceof OAuth2AuthenticationException oauth2Exception) {
            message = oauth2Exception.getError().getDescription();
        } else if (authException.getMessage() != null && !authException.getMessage().isBlank()) {
            message = authException.getMessage();
        }

        ApiResponse<Void> res = ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), message);
        objectMapper.writeValue(response.getWriter(), res);
    }
}
