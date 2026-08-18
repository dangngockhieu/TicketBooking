package com.ticketbooking.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    SUCCESS(0, "Thành công", HttpStatus.OK),

    // Common / Generic Errors
    INTERNAL_SERVER_ERROR(9999, "Lỗi hệ thống không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT_DATA(1001, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1002, "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    UNAUTHORIZED(1003, "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1004, "Bạn không có quyền thực hiện hành động này", HttpStatus.FORBIDDEN),

    // Auth & User Errors (2xxx)
    EMAIL_ALREADY_EXISTS(2001, "Email này đã được đăng ký trong hệ thống", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(2002, "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(2003, "Tài khoản của bạn đã bị khóa hoặc đang chờ duyệt", HttpStatus.FORBIDDEN),
    USER_NOT_FOUND(2004, "Không tìm thấy thông tin người dùng", HttpStatus.NOT_FOUND),

    // Catalog & Event Errors (3xxx)
    EVENT_NOT_FOUND(3001, "Sự kiện không tồn tại", HttpStatus.NOT_FOUND),
    TICKET_CLASS_NOT_FOUND(3002, "Hạng vé không tồn tại", HttpStatus.NOT_FOUND),
    EVENT_NOT_PUBLISHED(3003, "Sự kiện chưa được mở bán", HttpStatus.BAD_REQUEST),

    // Booking & Seat Hold Errors (4xxx)
    SEAT_HOLD_FAILED(4001, "Không thể giữ chỗ, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    SEAT_SOLD_OUT(4002, "Hạng vé này đã hết vé", HttpStatus.CONFLICT),
    BOOKING_NOT_FOUND(4003, "Không tìm thấy đơn đặt vé", HttpStatus.NOT_FOUND),
    BOOKING_EXPIRED(4004, "Thời gian giữ vé 10 phút đã hết hạn", HttpStatus.GONE),
    INVALID_ACCESS_TOKEN(4005, "Token phòng chờ không hợp lệ hoặc đã hết hạn", HttpStatus.FORBIDDEN),

    // Payment Errors (5xxx)
    PAYMENT_FAILED(5001, "Giao dịch thanh toán không thành công", HttpStatus.BAD_REQUEST),
    PAYMENT_CHECKSUM_INVALID(5002, "Chữ ký bảo mật thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_TRANSACTION_NOT_FOUND(5003, "Không tìm thấy giao dịch thanh toán", HttpStatus.NOT_FOUND);

    private final int status;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int status, String message, HttpStatus httpStatus) {
        this.status = status;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
