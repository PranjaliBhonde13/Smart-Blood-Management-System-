package com.sben.notification.email;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /** Due emails, most urgent first (security codes before announcements). */
    @Query("""
            SELECT e.id FROM EmailOutbox e
            WHERE e.status = com.sben.notification.email.EmailStatus.PENDING AND e.nextAttemptAt <= :now
            ORDER BY e.priority ASC, e.id ASC
            """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable page);

    /**
     * Atomically take one due email for sending. Returns 1 only for the caller
     * that won, so the after-commit worker and the scheduler never both send it.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE EmailOutbox e
            SET e.status = com.sben.notification.email.EmailStatus.SENDING, e.attempts = e.attempts + 1, e.lockedAt = :now
            WHERE e.id = :id AND e.status = com.sben.notification.email.EmailStatus.PENDING AND e.nextAttemptAt <= :now
            """)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Emails stuck in SENDING (the process died mid-send) go back to the queue. */
    @Modifying
    @Query("""
            UPDATE EmailOutbox e
            SET e.status = com.sben.notification.email.EmailStatus.PENDING, e.lockedAt = NULL
            WHERE e.status = com.sben.notification.email.EmailStatus.SENDING AND e.lockedAt < :before
            """)
    int releaseStuck(@Param("before") LocalDateTime before);

    /** Emails handed to the mail server since {@code since} (sent or in flight), for rate limiting. */
    @Query("""
            SELECT COUNT(e) FROM EmailOutbox e
            WHERE (e.status = com.sben.notification.email.EmailStatus.SENT AND e.sentAt >= :since)
               OR e.status = com.sben.notification.email.EmailStatus.SENDING
            """)
    long countDispatchedSince(@Param("since") LocalDateTime since);

    @Modifying
    @Query("""
            DELETE FROM EmailOutbox e
            WHERE e.status IN (com.sben.notification.email.EmailStatus.SENT, com.sben.notification.email.EmailStatus.SUPPRESSED)
              AND e.createdAt < :before
            """)
    int purgeDeliveredBefore(@Param("before") LocalDateTime before);

    /** Admin search; every filter is optional (null = ignore). */
    @Query("""
            SELECT e FROM EmailOutbox e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:category IS NULL OR e.category = :category)
              AND (:q IS NULL OR LOWER(e.recipient) LIKE :q OR LOWER(e.subject) LIKE :q)
            ORDER BY e.id DESC
            """)
    Page<EmailOutbox> search(@Param("status") EmailStatus status, @Param("category") EmailCategory category,
                             @Param("q") String q, Pageable pageable);

    long countByStatus(EmailStatus status);
}
