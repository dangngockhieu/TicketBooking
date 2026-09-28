package com.ticketbooking.notification.listener;

import com.ticketbooking.common.event.OrganizerWelcomeEvent;
import com.ticketbooking.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrganizerWelcomeEmailListenerTest {

    @Mock
    private EmailService emailService;

    private OrganizerWelcomeEmailListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrganizerWelcomeEmailListener(emailService, "support@ticketbooking.vn");
    }

    @Test
    void onOrganizerCreated_sendsWelcomeEmailWithTempPassword() {
        OrganizerWelcomeEvent event = OrganizerWelcomeEvent.builder()
                .eventType("auth.organizer-created")
                .email("organizer@eventpro.vn")
                .tempPassword("TempP@ssw0rd123!")
                .build();

        listener.onOrganizerCreated(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);

        verify(emailService).sendTemplatedEmail(
                eq("ORGANIZER_WELCOME"),
                isNull(),
                eq("organizer@eventpro.vn"),
                eq("Tài khoản Organizer TicketBooking của bạn đã sẵn sàng"),
                eq("organizer-welcome"),
                variablesCaptor.capture()
        );

        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        assertThat(capturedVariables)
                .containsEntry("email", "organizer@eventpro.vn")
                .containsEntry("tempPassword", "TempP@ssw0rd123!")
                .containsEntry("supportEmail", "support@ticketbooking.vn")
                .containsKey("year");
    }
}
