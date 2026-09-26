package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.dto.response.TicketClassReportRow;
import com.ticketbooking.booking.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

        Optional<Ticket> findByQrCodeData(String qrCodeData);

        /**
         * Check-in atomic: chỉ chuyển sang CHECKED_IN khi đang ISSUED — trả về số
         * dòng bị ảnh hưởng (0 hoặc 1) để chống quét trùng (2 lượt quét cùng lúc
         * cùng một vé), xem docs/technical-flows.md §5.2.
         */
        @Modifying
        @Query("UPDATE Ticket t SET t.status = com.ticketbooking.booking.enums.TicketStatus.CHECKED_IN, "
                        + "t.checkedInAt = :checkedInAt, t.updatedAt = :checkedInAt "
                        + "WHERE t.id = :id AND t.status = com.ticketbooking.booking.enums.TicketStatus.ISSUED")
        int checkInIfIssued(@Param("id") UUID id, @Param("checkedInAt") Instant checkedInAt);

        /**
         * Số vé đã bán (ISSUED/CHECKED_IN, loại trừ LOCKED/CANCELLED)/đã check-in/
         * doanh thu theo từng hạng vé của một sự kiện — nguồn cho báo cáo Organizer
         * Doanh thu tính từ snapshot
         * {@code unit_price} lúc đặt vé (không trừ phí nền tảng/refund — xem
         * {@code BookingServiceImpl#getEventReport} để biết phạm vi báo cáo).
         */
        @Query("SELECT new com.ticketbooking.booking.dto.response.TicketClassReportRow("
                        + "t.ticketClassId, t.ticketClassName, COUNT(t), "
                        + "SUM(CASE WHEN t.status = com.ticketbooking.booking.enums.TicketStatus.CHECKED_IN THEN 1L ELSE 0L END), "
                        + "SUM(t.unitPrice)) "
                        + "FROM Ticket t JOIN t.booking b "
                        + "WHERE b.eventId = :eventId "
                        + "AND t.status IN (com.ticketbooking.booking.enums.TicketStatus.ISSUED, com.ticketbooking.booking.enums.TicketStatus.CHECKED_IN) "
                        + "GROUP BY t.ticketClassId, t.ticketClassName")
        List<TicketClassReportRow> aggregateSoldByTicketClass(@Param("eventId") UUID eventId);
}
