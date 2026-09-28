package com.ticketbooking.notification.listener;

import com.ticketbooking.common.event.TicketsGeneratedEvent;
import com.ticketbooking.notification.repository.EmailLogRepository;
import com.ticketbooking.notification.service.EmailService;
import com.ticketbooking.notification.service.QrCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumer Kafka {@code tickets.generated} — gửi email E-Ticket kèm QR cho
 * từng vé (xem docs/development-plan.md GĐ4 mục 2, Consumer 3). Idempotent:
 * bỏ qua nếu {@code bookingId} đã từng gửi thành công trước đó (Kafka
 * redeliver message), khác với Catalog Service (trừ kho — có guard riêng)
 * consumer này cũng lắng nghe cùng topic một cách độc lập.
 */
@Slf4j
@Component
public class TicketsGeneratedEmailListener {

    private static final String EMAIL_TYPE = "TICKET_ISSUED";
    private static final String TEMPLATE = "ticket-issued";

    private final EmailService emailService;
    private final QrCodeService qrCodeService;
    private final EmailLogRepository emailLogRepository;
    private final String supportEmail;

    public TicketsGeneratedEmailListener(
            EmailService emailService,
            QrCodeService qrCodeService,
            EmailLogRepository emailLogRepository,
            @Value("${notification.mail.support-email}") String supportEmail) {
        this.emailService = emailService;
        this.qrCodeService = qrCodeService;
        this.emailLogRepository = emailLogRepository;
        this.supportEmail = supportEmail;
    }

    @KafkaListener(topics = "tickets.generated")
    public void onTicketsGenerated(TicketsGeneratedEvent event) {
        String bookingId = event.getBookingId().toString();

        if (event.getCustomerEmail() == null || event.getCustomerEmail().isBlank()) {
            log.warn("Bỏ qua gửi E-Ticket cho booking {} — không lấy được email khách hàng.", bookingId);
            return;
        }
        if (emailLogRepository.existsByTypeAndDedupeKeyAndStatus(EMAIL_TYPE, bookingId, "SENT")) {
            log.info("Booking {} đã gửi E-Ticket thành công trước đó, bỏ qua (idempotent).", bookingId);
            return;
        }

        List<TicketsGeneratedEvent.TicketDetail> tickets = event.getTickets();
        if (tickets == null || tickets.isEmpty()) {
            log.warn("Booking {} không có chi tiết vé (tickets rỗng) — bỏ qua gửi E-Ticket.", bookingId);
            return;
        }

        Map<String, byte[]> inlineImages = new HashMap<>();
        List<Map<String, Object>> ticketViews = tickets.stream().map(ticket -> {
            String contentId = "qr-" + ticket.getTicketId();
            inlineImages.put(contentId, qrCodeService.generatePng(ticket.getQrCodeData()));
            return Map.<String, Object>of(
                    "ticketClassName", ticket.getTicketClassName(),
                    "qrCid", "cid:" + contentId);
        }).toList();

        Map<String, Object> variables = Map.of(
                "eventTitle", event.getEventTitle() != null ? event.getEventTitle() : "Sự kiện của bạn",
                "tickets", ticketViews,
                "ticketCount", tickets.size(),
                "supportEmail", supportEmail,
                "year", Year.now().getValue());

        emailService.sendTemplatedEmailWithInlineImages(
                EMAIL_TYPE, bookingId, event.getCustomerEmail(),
                "Vé điện tử của bạn — " + variables.get("eventTitle"),
                TEMPLATE, variables, inlineImages);
    }
}
