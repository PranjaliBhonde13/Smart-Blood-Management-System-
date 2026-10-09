package com.sben.notification.email;

import com.sben.common.exception.BusinessException;
import com.sben.notification.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Delivers queued emails.
 *
 * <ul>
 *   <li>Right after the queuing transaction commits, the email is sent on the
 *       mail thread pool, so one-time codes arrive within seconds and the
 *       user's request never waits for the mail server.</li>
 *   <li>A scheduled sweep retries failures with backoff (1, 5, 15, 60, 180
 *       minutes), recovers emails stuck by a crash, and sends anything the
 *       immediate path skipped.</li>
 *   <li>Per-minute and per-day caps keep within the SMTP provider's limits
 *       (Gmail allows roughly 500 recipients a day); over the cap, emails wait.</li>
 *   <li>Each email is claimed atomically before sending, so it is never sent twice.</li>
 * </ul>
 */
@Slf4j
@Component
public class EmailDispatcher {

    private static final int[] BACKOFF_MINUTES = {1, 5, 15, 60, 180};
    static final String REDACTED = "[removed after delivery: contained a one-time code]";

    private final EmailOutboxRepository repository;
    private final EmailService transport;
    private final TransactionTemplate tx;
    private final int maxAttempts;
    private final int maxPerMinute;
    private final int maxPerDay;
    private final int batchSize;
    private final int retentionDays;

    public EmailDispatcher(EmailOutboxRepository repository, EmailService transport, PlatformTransactionManager txManager,
                           @Value("${app.mail.max-attempts:6}") int maxAttempts,
                           @Value("${app.mail.max-per-minute:20}") int maxPerMinute,
                           @Value("${app.mail.max-per-day:450}") int maxPerDay,
                           @Value("${app.mail.batch-size:25}") int batchSize,
                           @Value("${app.mail.retention-days:90}") int retentionDays) {
        this.repository = repository;
        this.transport = transport;
        this.tx = new TransactionTemplate(txManager);
        this.maxAttempts = maxAttempts;
        this.maxPerMinute = maxPerMinute;
        this.maxPerDay = maxPerDay;
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
    }

    /** Immediate delivery once the transaction that queued the email has committed. */
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(EmailOutboxService.QueuedEvent event) {
        deliver(event.id());
    }

    /** Retries, crash recovery and anything not delivered immediately. */
    @Scheduled(fixedDelayString = "${app.mail.dispatch-interval-ms:15000}",
               initialDelayString = "${app.mail.dispatch-initial-delay-ms:20000}")
    public void dispatchDue() {
        LocalDateTime now = LocalDateTime.now();
        Integer released = tx.execute(s -> repository.releaseStuck(now.minusMinutes(5)));
        if (released != null && released > 0) log.warn("Re-queued {} email(s) stuck in SENDING", released);
        List<Long> due = repository.findDueIds(now, PageRequest.of(0, batchSize));
        for (Long id : due) {
            if (!deliver(id)) break; // rate limit reached: the rest waits for the next sweep
        }
    }

    /** Delete delivered/suppressed emails older than the retention period. Failed ones are kept for review. */
    @Scheduled(cron = "${app.mail.purge-cron:0 30 3 * * *}")
    public void purgeOld() {
        Integer n = tx.execute(s -> repository.purgeDeliveredBefore(LocalDateTime.now().minusDays(retentionDays)));
        if (n != null && n > 0) log.info("Purged {} delivered email(s) older than {} days", n, retentionDays);
    }

    /**
     * Try to send one email now.
     *
     * @return false only when a rate limit stopped it (the email stays queued)
     */
    public boolean deliver(Long id) {
        LocalDateTime now = LocalDateTime.now();
        if (repository.countDispatchedSince(now.minusMinutes(1)) >= maxPerMinute
                || repository.countDispatchedSince(now.minusDays(1)) >= maxPerDay) {
            log.debug("Email rate limit reached; #{} stays queued", id);
            return false;
        }
        Integer claimed = tx.execute(s -> repository.claim(id, now));
        if (claimed == null || claimed == 0) return true; // already taken, sent, or not yet due

        EmailOutbox e = repository.findById(id).orElse(null);
        if (e == null) return true;
        try {
            transport.deliver(new OutgoingEmail(e.getId(), e.getRecipient(), e.getSubject(), e.getHtmlBody(), e.getTextBody()));
            finish(id, EmailStatus.SENT, null);
        } catch (EmailDeliveryException ex) {
            failed(e, ex.isPermanent(), ex.getMessage());
        } catch (RuntimeException ex) {
            failed(e, false, ex.getMessage());
        }
        return true;
    }

    /** Administrator re-queues a failed email. Emails whose one-time code was wiped cannot be resent. */
    public void retry(Long id) {
        tx.executeWithoutResult(s -> {
            EmailOutbox e = repository.findById(id)
                    .orElseThrow(() -> new BusinessException("Email not found", HttpStatus.NOT_FOUND));
            if (e.getStatus() != EmailStatus.FAILED) {
                throw new BusinessException("Only failed emails can be retried");
            }
            if (e.isSensitive()) {
                throw new BusinessException("This email contained a one-time code that has been removed. "
                        + "Ask the user to request a new code instead.");
            }
            e.setStatus(EmailStatus.PENDING);
            e.setAttempts(0);
            e.setNextAttemptAt(LocalDateTime.now());
            e.setLastError(null);
            repository.save(e);
        });
    }

    // ---------- helpers ----------

    private void failed(EmailOutbox e, boolean permanent, String error) {
        int attempts = e.getAttempts() + 1; // the claim incremented it in the database
        if (permanent || attempts >= maxAttempts) {
            log.error("Email #{} ({}) to {} failed permanently after {} attempt(s): {}",
                    e.getId(), e.getTemplate(), e.getRecipient(), attempts, error);
            finish(e.getId(), EmailStatus.FAILED, error);
        } else {
            int minutes = BACKOFF_MINUTES[Math.min(attempts - 1, BACKOFF_MINUTES.length - 1)];
            log.warn("Email #{} to {} failed (attempt {}), retrying in {} min: {}",
                    e.getId(), e.getRecipient(), attempts, minutes, error);
            tx.executeWithoutResult(s -> repository.findById(e.getId()).ifPresent(row -> {
                row.setStatus(EmailStatus.PENDING);
                row.setLockedAt(null);
                row.setNextAttemptAt(LocalDateTime.now().plusMinutes(minutes));
                row.setLastError(EmailOutboxService.truncate(error, 500));
                repository.save(row);
            }));
        }
    }

    private void finish(Long id, EmailStatus status, String error) {
        tx.executeWithoutResult(s -> repository.findById(id).ifPresent(row -> {
            row.setStatus(status);
            row.setLockedAt(null);
            row.setLastError(EmailOutboxService.truncate(error, 500));
            if (status == EmailStatus.SENT) row.setSentAt(LocalDateTime.now());
            if (row.isSensitive()) { // one-time codes don't stay in the database
                row.setHtmlBody(REDACTED);
                row.setTextBody(REDACTED);
            }
            repository.save(row);
        }));
    }
}
