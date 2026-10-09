package com.sben.notification;

import com.sben.common.exception.BusinessException;
import com.sben.notification.email.EmailCategory;
import com.sben.notification.email.EmailContent;
import com.sben.notification.email.EmailOutboxService;
import com.sben.notification.email.EmailRenderer;
import com.sben.user.Role;
import com.sben.patient.PatientRepository;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates in-app notifications and mirrors them to email. This is the single
 * entry point other modules call when something happens the user should know
 * about (request accepted, status changed, account verified, …).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailOutboxService outbox;
    private final EmailRenderer renderer;
    private final PatientRepository patientRepository;

    /**
     * Persist an in-app notification and queue a matching email. The email is
     * written to the outbox in this transaction and delivered after commit
     * (with retries), so it can neither slow down nor roll back the action.
     */
    @Transactional
    public void notify(Long userId, String title, String message, Notification.Type type) {
        notify(userId, title, message, type, categoryFor(type));
    }

    /** As {@link #notify(Long, String, String, Notification.Type)} with an explicit email category (e.g. announcements). */
    @Transactional
    public void notify(Long userId, String title, String message, Notification.Type type, EmailCategory category) {
        repository.save(Notification.builder()
                .userId(userId).title(title).message(message)
                .type(type).status(Notification.Status.UNREAD)
                .build());
        if (type == Notification.Type.REQUEST && patientOptedOutOfEmail(userId)) return; // in-app copy is kept
        userRepository.findById(userId)
                // Only active accounts with a confirmed address get email.
                .filter(u -> u.isEnabled() && !u.isDeleted() && u.isEmailVerified())
                .ifPresent(u -> outbox.enqueue(u.getId(), u.getEmail(), category, "NOTIFICATION_" + type.name(),
                        notificationEmail(u, title, message, type), false));
    }

    /** The email version of an in-app notification for this user (also used for template previews). */
    public EmailContent notificationEmail(User u, String title, String message, Notification.Type type) {
        return EmailContent.builder()
                .subject(title)
                .preheader(preview(message))
                .heading(title)
                .paragraph("Hi " + firstName(u) + ",")
                .paragraph(message)
                .actionLabel(actionLabel(type))
                .actionUrl(renderer.link(actionPath(u.getRole(), type)))
                .tone(type == Notification.Type.VERIFICATION ? EmailContent.Tone.SUCCESS : null)
                .build();
    }

    static EmailCategory categoryFor(Notification.Type type) {
        return switch (type) {
            case REQUEST -> EmailCategory.REQUEST;
            case DONATION -> EmailCategory.DONATION;
            case VERIFICATION -> EmailCategory.VERIFICATION;
            case SYSTEM -> EmailCategory.ACCOUNT;
        };
    }

    /** Where the email's button takes this user, by role and what happened. */
    static String actionPath(Role role, Notification.Type type) {
        return switch (role) {
            case PATIENT -> type == Notification.Type.VERIFICATION ? "/patient/profile" : "/patient/requests";
            case DONOR -> type == Notification.Type.DONATION ? "/donor/donations"
                    : type == Notification.Type.REQUEST ? "/donor/open-requests" : "/donor";
            case HOSPITAL -> type == Notification.Type.VERIFICATION ? "/hospital/profile" : "/hospital/queue";
            case BLOOD_BANK -> type == Notification.Type.VERIFICATION ? "/blood-bank/profile" : "/blood-bank/requests";
            case ADMIN -> type == Notification.Type.VERIFICATION ? "/admin/verifications"
                    : type == Notification.Type.REQUEST ? "/admin/requests" : "/admin";
        };
    }

    private static String actionLabel(Notification.Type type) {
        return switch (type) {
            case REQUEST -> "View request";
            case DONATION -> "View donation";
            case VERIFICATION -> "Open your profile";
            case SYSTEM -> "Open Smart Blood";
        };
    }

    private static String firstName(User u) {
        String n = u.getFullName() == null ? "" : u.getFullName().trim();
        return n.isEmpty() ? "there" : n.split("\\s+")[0];
    }

    private static String preview(String message) {
        if (message == null) return "";
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 117) + "...";
    }

    /** A patient who explicitly declined request-update emails (NULL = not answered = still emailed). */
    private boolean patientOptedOutOfEmail(Long userId) {
        return patientRepository.findByUserId(userId)
                .map(p -> Boolean.FALSE.equals(p.getNotificationConsent()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndStatus(userId, Notification.Status.UNREAD);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Notification not found", HttpStatus.NOT_FOUND));
        if (!n.getUserId().equals(userId)) {
            throw new BusinessException("Not your notification", HttpStatus.FORBIDDEN);
        }
        n.setStatus(Notification.Status.READ);
        repository.save(n);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId);
    }
}
