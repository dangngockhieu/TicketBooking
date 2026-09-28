package com.ticketbooking.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Kafka topic {@code auth.organizer-created} — Auth Service bắn khi Admin cấp
 * tài khoản Organizer mới (xem docs/api-design.md §1.5, AuthServiceImpl#createOrganizer),
 * Notification Service lắng nghe để gửi email chào mừng kèm mật khẩu tạm.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerWelcomeEvent extends BaseEvent {

    private String email;

    private String tempPassword;
}
