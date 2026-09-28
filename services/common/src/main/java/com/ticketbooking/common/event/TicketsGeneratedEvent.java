package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Kafka topic {@code tickets.generated} — Booking Service bắn sau khi xác
 * nhận thanh toán thành công, Catalog Service lắng nghe để trừ vĩnh viễn
 * {@code available_quantity}
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TicketsGeneratedEvent extends BaseEvent {

    private UUID bookingId;

    /**
     * Sự kiện (concert/show) bên Catalog Service — đặt tên khác {@code eventId} của
     * {@link BaseEvent} (đó là id của bản thân message Kafka).
     */
    private UUID catalogEventId;

    private List<TicketClassQuantity> items;

    /**
     * Email khách hàng + chi tiết từng vé (QR) — dùng bởi Notification Service
     * để gửi E-Ticket, KHÔNG dùng bởi Catalog Service (chỉ cần {@link #items}
     * để trừ kho). {@code customerEmail}/{@code eventTitle} có thể {@code null}
     * nếu Booking Service không lấy được từ Auth/Catalog Service tại thời điểm
     * publish — Notification Service bỏ qua gửi mail (không chặn luồng xác
     * nhận thanh toán) khi thiếu email.
     */
    private String customerEmail;

    private String eventTitle;

    private List<TicketDetail> tickets;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketClassQuantity implements Serializable {
        private UUID ticketClassId;
        private int quantity;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketDetail implements Serializable {
        private UUID ticketId;
        private String ticketClassName;
        private String qrCodeData;
    }
}
