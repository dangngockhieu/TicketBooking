package com.ticketbooking.common.exception;

/** Một dịch vụ nội bộ khác không phản hồi kịp trong thời gian cấu hình (connect/read timeout). */
public class TimeoutException extends RuntimeException {
    public TimeoutException(String message) {
        super(message);
    }
}
