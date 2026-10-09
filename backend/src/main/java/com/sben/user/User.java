package com.sben.user;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Central identity/credential record. Role-specific profile data (patient,
 * donor, hospital, blood bank) lives in separate 1:1 tables added in Module 2.
 *
 * @see com.sben.user.Role
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 15)
    private String phone;

    /** BCrypt hash — plaintext passwords are never stored. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Account cannot authenticate until the email OTP is confirmed. */
    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified = false;

    /** Admin can suspend an account without deleting it. */
    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    /** Shown to the user at sign-in while the account is suspended. */
    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    /**
     * Access tokens issued before this instant are rejected. Bumped on logout,
     * password reset and suspension so a stolen/stale JWT stops working at once.
     */
    @Column(name = "tokens_valid_after")
    private LocalDateTime tokensValidAfter;

    /** Invalidate every access token issued up to now. */
    public void revokeIssuedTokens() {
        this.tokensValidAfter = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
