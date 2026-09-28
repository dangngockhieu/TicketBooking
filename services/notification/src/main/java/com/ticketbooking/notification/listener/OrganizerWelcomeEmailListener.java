package com.ticketbooking.notification.listener;

import com.ticketbooking.common.event.OrganizerWelcomeEvent;
import com.ticketbooking.notification.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.Map;

/**
 * Consumer Kafka {@code auth.organizer-created} — gửi email chào mừng kèm
 * mật khẩu tạm cho Organizer mới do Admin cấp tài khoản (xem
 * docs/api-design.md §1.5). Không dedupe — mỗi lần Admin tạo tài khoản là
 * một hành động nghiệp vụ hợp lệ, không phải Kafka redeliver.
 */
@Slf4j
@Component
public class OrganizerWelcomeEmailListener {

    private static final String EMAIL_TYPE = "ORGANIZER_WELCOME";
    private static final String TEMPLATE = "organizer-welcome";

    private final EmailService emailService;
    private final String supportEmail;

    public OrganizerWelcomeEmailListener(EmailService emailService, @Value("${notification.mail.support-email}") String supportEmail) {
        this.emailService = emailService;
        this.supportEmail = supportEmail;
    }

    @KafkaListener(topics = "auth.organizer-created")
    public void onOrganizerCreated(OrganizerWelcomeEvent event) {
        Map<String, Object> variables = Map.of(
                "email", event.getEmail(),
                "tempPassword", event.getTempPassword(),
                "supportEmail", supportEmail,
                "year", Year.now().getValue());

        emailService.sendTemplatedEmail(EMAIL_TYPE, null, event.getEmail(),
                "Tài khoản Organizer TicketBooking của bạn đã sẵn sàng", TEMPLATE, variables);
    }
}
