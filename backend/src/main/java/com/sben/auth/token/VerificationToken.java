package com.sben.auth.token;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Short-lived numeric code used for email verification (EMAIL_OTP) and password
 * reset (PASSWORD_RESET). Single-use: {@code used} flips true on consumption.
 */
@Entity
@Table(name = "verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    public enum Type { EMAIL_OTP, PASSWORD_RESET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isValid() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }
}
