package com.sben.notification;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An in-app message for a user (bell/inbox). The same events also trigger an
 * email via {@link EmailService}; this row is the persistent, in-product record.
 */
@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    public enum Type { REQUEST, DONATION, SYSTEM, VERIFICATION }
    public enum Status { READ, UNREAD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type = Type.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UNREAD;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
