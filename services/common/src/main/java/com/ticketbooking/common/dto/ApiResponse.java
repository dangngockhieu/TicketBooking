package com.ticketbooking.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private int status = 0;

    private String message;

    private T data;

    private Map<String, String> errors;

    @Builder.Default
    private long responseTime = System.currentTimeMillis();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(0)
                .message("Thành công")
                .data(data)
                .responseTime(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(0)
                .message(message)
                .data(data)
                .responseTime(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(null)
                .errors(null)
                .responseTime(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, Map<String, String> errors) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(null)
                .errors(errors)
                .responseTime(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(int status, String message, T data, Map<String, String> errors) {
        return ApiResponse.<T>builder()
                .status(status)
                .message(message)
                .data(data)
                .errors(errors)
                .responseTime(System.currentTimeMillis())
                .build();
    }
}
