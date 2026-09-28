package com.ticketbooking.notification.service.impl;

import com.ticketbooking.notification.entity.EmailLog;
import com.ticketbooking.notification.repository.EmailLogRepository;
import com.ticketbooking.notification.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.util.Map;

/**
 * Render template Thymeleaf (biến {@code {{tenBien}}} trong template gốc
 * tương ứng {@code [[${tenBien}]]} — xem
 * TicketBooking-Frontend/email-templates/README.md) rồi gửi qua SMTP thật.
 * Luôn ghi {@link EmailLog} (kể cả khi gửi thất bại) để tra soát — KHÔNG ném
 * lỗi ra ngoài (email là tính năng phụ trợ, không được làm rớt luồng nghiệp
 * vụ chính gọi vào đây, xem các Kafka listener).
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;
    private final String fromAddress;

    public EmailServiceImpl(
            JavaMailSender mailSender,
            SpringTemplateEngine templateEngine,
            EmailLogRepository emailLogRepository,
            @Value("${notification.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.emailLogRepository = emailLogRepository;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendTemplatedEmail(String type, String dedupeKey, String to, String subject, String template,
            Map<String, Object> variables) {
        sendTemplatedEmailWithInlineImages(type, dedupeKey, to, subject, template, variables, Map.of());
    }

    @Override
    public void sendTemplatedEmailWithInlineImages(String type, String dedupeKey, String to, String subject,
            String template, Map<String, Object> variables, Map<String, byte[]> inlineImagesByContentId) {
        try {
            Context context = new Context(LocaleContextHolder.getLocale());
            context.setVariables(variables);
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,
                    !inlineImagesByContentId.isEmpty(), "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            for (Map.Entry<String, byte[]> entry : inlineImagesByContentId.entrySet()) {
                helper.addInline(entry.getKey(), () -> new java.io.ByteArrayInputStream(entry.getValue()), "image/png");
            }

            mailSender.send(message);
            log.info("Đã gửi email {} tới {}", type, to);
            saveLog(type, dedupeKey, to, subject, "SENT", null);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Gửi email {} tới {} thất bại: {}", type, to, e.getMessage());
            saveLog(type, dedupeKey, to, subject, "FAILED", e.getMessage());
        }
    }

    private void saveLog(String type, String dedupeKey, String to, String subject, String status, String error) {
        try {
            emailLogRepository.save(EmailLog.builder()
                    .type(type)
                    .dedupeKey(dedupeKey)
                    .recipient(to)
                    .subject(subject)
                    .status(status)
                    .errorMessage(error)
                    .sentAt(Instant.now())
                    .build());
        } catch (RuntimeException e) {
            log.warn("Không ghi được EmailLog (MongoDB): {}", e.getMessage());
        }
    }
}
