package com.sben.notification.email;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One email waiting for, or done with, delivery. See V12__email_delivery.sql. */
@Entity
@Table(name = "email_outbox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 150)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailCategory category;

    @Column(nullable = false, length = 40)
    private String template;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String htmlBody;

    @Column(name = "text_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String textBody;

    /** Contains a one-time code or similar: the bodies are wiped once delivery finishes. */
    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
