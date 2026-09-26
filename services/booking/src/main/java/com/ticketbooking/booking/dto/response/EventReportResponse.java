package com.ticketbooking.booking.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Báo cáo doanh thu/tỷ lệ lấp đầy/tỷ lệ check-in cho Organizer Dashboard (xem
 * {@code totalRevenue} tính từ snapshot
 * {@code unit_price} của các vé ISSUED/CHECKED_IN — CHƯA trừ phí nền tảng
 * ({@code commissionRate}/{@code flatFeePerTicket} bên Catalog Service) hay
 * hoàn tiền, đây là doanh thu gộp theo góc nhìn vé đã bán, không phải số tiền
 * thực nhận về ví Organizer (số đó do Payment Service quản lý qua
 * {@code OrganizerWallet}).
 */
public record EventReportResponse(
                UUID eventId,
                String eventTitle,
                BigDecimal totalRevenue,
                long totalTicketsSold,
                long totalCapacity,
                double fillRate,
                long totalCheckedIn,
                double checkInRate,
                List<TicketClassReport> byTicketClass) {
        public record TicketClassReport(
                        UUID ticketClassId,
                        String name,
                        long totalQuantity,
                        long sold,
                        long checkedIn,
                        BigDecimal revenue,
                        double fillRate,
                        double checkInRate) {
        }
}
