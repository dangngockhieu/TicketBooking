package com.ticketbooking.queue.security;

import com.ticketbooking.queue.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Xác thực khung STOMP {@code CONNECT} bằng JWT — trình duyệt không thể đính
 * kèm header {@code Authorization} khi mở kết nối WebSocket thô, nên client
 * gửi Bearer token dưới dạng STOMP header (STOMP.js hỗ trợ truyền header tùy ý
 * ở bước connect, xem docs/virtual-waiting-room.md). {@link Principal} của
 * phiên STOMP được gán bằng chính {@code userId} (dạng chuỗi UUID) để
 * {@code SimpMessagingTemplate.convertAndSendToUser} định tuyến đúng người.
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("STOMP CONNECT thiếu Bearer token, từ chối kết nối.");
            return null;
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorizationHeader.substring(7));
            UUID userId = SecurityUtil.extractUserId(jwt)
                    .orElseThrow(() -> new JwtException("JWT thiếu claim userId"));

            Principal principal = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
            accessor.setUser(principal);
            return message;
        } catch (JwtException e) {
            log.warn("STOMP CONNECT với JWT không hợp lệ: {}", e.getMessage());
            return null;
        }
    }
}
