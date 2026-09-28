package com.ticketbooking.notification.service;

import java.util.Map;

public interface EmailService {

    /** Gửi email HTML render từ template Thymeleaf, không có ảnh nhúng (dùng cho OTP). */
    void sendTemplatedEmail(String type, String dedupeKey, String to, String subject, String template,
            Map<String, Object> variables);

    /**
     * Gửi email HTML kèm ảnh nhúng inline theo Content-ID (dùng cho E-Ticket —
     * mỗi vé 1 ảnh QR riêng, tham chiếu trong template bằng {@code cid:<key>}).
     */
    void sendTemplatedEmailWithInlineImages(String type, String dedupeKey, String to, String subject,
            String template, Map<String, Object> variables, Map<String, byte[]> inlineImagesByContentId);
}
