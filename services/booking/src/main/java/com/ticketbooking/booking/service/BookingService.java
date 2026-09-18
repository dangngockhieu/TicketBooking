package com.ticketbooking.booking.service;

import com.ticketbooking.booking.dto.request.CreateBookingRequest;
import com.ticketbooking.booking.dto.response.BookingResponse;
import com.ticketbooking.booking.enums.BookingStatus;
import com.ticketbooking.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface BookingService {

    BookingResponse create(UUID customerId, CreateBookingRequest request);

    /** Chỉ chủ đơn mới được xem — ném ForbiddenException nếu không phải chủ đơn. */
    BookingResponse getDetail(UUID customerId, UUID bookingId);

    PageResponse<BookingResponse> listMine(UUID customerId, BookingStatus status, Pageable pageable);

    /** Hủy đơn theo yêu cầu khách hàng — chỉ khi đang PENDING_PAYMENT. */
    void cancel(UUID customerId, UUID bookingId);

    /**
     * Nhả ghế hệ thống (hết hạn giữ chỗ) — dùng bởi
     * {@code BookingExpirationScheduler} và Redis keyspace notification
     * listener, KHÔNG kiểm tra quyền sở hữu. No-op nếu booking không còn ở
     * trạng thái PENDING_PAYMENT (đã được xử lý bởi nhánh khác).
     */
    void releaseExpiredBooking(UUID bookingId);

    /**
     * Xác nhận thanh toán thành công (Kafka consumer {@code payment.success},
     * xem docs/development-plan.md GĐ4 mục 2) — chuyển booking sang PAID, vé
     * sang ISSUED, trả lại Redis seat hold, rồi bắn tiếp {@code tickets.generated}.
     * <p>
     * Nếu không thể hoàn tất (booking không tồn tại, đã bị auto-release do hết
     * hạn giữ chỗ, hoặc lỗi hệ thống giữa chừng) — bắn
     * {@code booking.refund-requested} (Saga Compensation, xem
     * docs/api-design.md §5.4) để Payment Service tự động hoàn tiền qua MoMo.
     * No-op (không refund) nếu booking đã được xử lý bởi lần redeliver trước
     * (đã PAID/REFUNDED).
     *
     * @param transactionId  giao dịch MoMo tương ứng — cần để build refund request nếu phải compensate
     * @param amount         số tiền đã thanh toán — cần để build refund request
     * @param gatewayTransId transId gốc bên MoMo — cần để build refund request
     */
    void confirmPayment(UUID bookingId, UUID transactionId, BigDecimal amount, String gatewayTransId);

    /**
     * Hoàn tất Saga Compensation (Kafka consumer {@code payment.refunded}, xem
     * docs/api-design.md §5.4) — chuyển booking sang REFUNDED. Nếu booking vẫn
     * còn PENDING_PAYMENT (nghĩa là {@link #confirmPayment} chưa từng kịp nhả
     * ghế trước khi lỗi xảy ra), release nốt vé/Redis seat hold ở đây. No-op
     * nếu booking không tồn tại hoặc đã REFUNDED (idempotent).
     */
    void markRefunded(UUID bookingId);
}
