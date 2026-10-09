package com.sben.auth.device;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A browser/network a user has signed in from. See V12__email_delivery.sql. */
@Entity
@Table(name = "user_login_devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex of the device description and network prefix. */
    @Column(nullable = false, columnDefinition = "CHAR(64)")
    private String fingerprint;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
