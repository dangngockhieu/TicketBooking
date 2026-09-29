package com.ticketbooking.auth.entity;

import com.ticketbooking.auth.enums.ClientType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // SHA-256 hash (hex) của refresh token thô, không phải JWT gốc — xem
    // AuthServiceImpl#hashToken. Độ dài cố định 64 ký tự, không phụ thuộc số
    // claim trong JWT, và tránh lưu token thô có thể dùng lại nếu DB bị lộ.
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    @Builder.Default
    private ClientType clientType = ClientType.WEB;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiredAt);
    }
}
