package com.sben.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Queues emails. Called inside the business transaction, so an email exists
 * only if the action that caused it committed. Delivery happens afterwards in
 * {@link EmailDispatcher} (right after commit, with scheduled retries).
 *
 * <p>Addresses on suppressed domains (test/demo domains such as the seeded
 * {@code @sben.com} accounts) are recorded but never sent, so demo data can
 * never email real third parties.
 */
@Slf4j
@Service
public class EmailOutboxService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public record QueuedEvent(Long id) {}

    private final EmailOutboxRepository repository;
    private final EmailRenderer renderer;
    private final ApplicationEventPublisher events;
    private final Set<String> suppressedDomains;

    public EmailOutboxService(EmailOutboxRepository repository, EmailRenderer renderer, ApplicationEventPublisher events,
                              @Value("${app.mail.suppressed-domains:example.com,example.org,example.net,example.test,sben.com}")
                              String suppressedDomains) {
        this.repository = repository;
        this.renderer = renderer;
        this.events = events;
        this.suppressedDomains = Arrays.stream(suppressedDomains.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /**
     * Render and queue one email. Returns the outbox id.
     *
     * @param sensitive the email carries a one-time code: its bodies are wiped once delivery finishes
     */
    @Transactional
    public Long enqueue(Long userId, String to, EmailCategory category, String template,
                        EmailContent content, boolean sensitive) {
        String recipient = to == null ? "" : to.trim().toLowerCase(Locale.ROOT);
        EmailRenderer.Rendered r = renderer.render(content, category);
        String suppressedReason = suppressionReason(recipient);

        EmailOutbox row = repository.save(EmailOutbox.builder()
                .userId(userId)
                .recipient(truncate(recipient.isEmpty() ? "(none)" : recipient, 150))
                .category(category)
                .template(truncate(template, 40))
                .priority(category.priority())
                .subject(truncate(r.subject(), 200))
                .htmlBody(suppressedReason != null && sensitive ? "[not sent]" : r.html())
                .textBody(suppressedReason != null && sensitive ? "[not sent]" : r.text())
                .sensitive(sensitive)
                .status(suppressedReason == null ? EmailStatus.PENDING : EmailStatus.SUPPRESSED)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .lastError(suppressedReason)
                .build());

        if (suppressedReason == null) {
            events.publishEvent(new QueuedEvent(row.getId()));
        } else {
            log.info("Email #{} ({}) to {} not sent: {}", row.getId(), template, recipient, suppressedReason);
        }
        return row.getId();
    }

    /** Why this address must not receive mail, or null if it may. */
    String suppressionReason(String recipient) {
        if (!EMAIL.matcher(recipient).matches()) return "Invalid email address";
        String domain = recipient.substring(recipient.lastIndexOf('@') + 1);
        for (String d : suppressedDomains) {
            if (domain.equals(d) || domain.endsWith("." + d)) return "Suppressed domain (" + d + ")";
        }
        return null;
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
