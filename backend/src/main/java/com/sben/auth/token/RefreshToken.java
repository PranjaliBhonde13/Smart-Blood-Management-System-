package com.sben.auth.token;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Server-side record of an issued refresh token. Persisting these enables
 * rotation (issue new, revoke old) and "logout everywhere" (revoke all for a
 * user) — impossible with stateless-only JWTs.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return !revoked && expiresAt.isAfter(LocalDateTime.now());
    }
}
