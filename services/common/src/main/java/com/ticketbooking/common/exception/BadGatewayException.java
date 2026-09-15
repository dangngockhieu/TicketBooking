package com.ticketbooking.common.exception;

/** Lỗi kết nối/xử lý tới một cổng dịch vụ bên ngoài (vd: MoMo Payment Gateway). */
public class BadGatewayException extends RuntimeException {
    public BadGatewayException(String message) {
        super(message);
    }
}
