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

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketClassQuantity implements Serializable {
        private UUID ticketClassId;
        private int quantity;
    }
}
