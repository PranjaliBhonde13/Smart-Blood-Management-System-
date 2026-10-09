package com.sben.notification;

import com.sben.common.exception.BusinessException;
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
    private final EmailService emailService;
    private final PatientRepository patientRepository;

    /**
     * Persist an in-app notification and send a matching email (best-effort:
     * an email failure must not roll back the user's action).
     */
    @Transactional
    public void notify(Long userId, String title, String message, Notification.Type type) {
        repository.save(Notification.builder()
                .userId(userId).title(title).message(message)
                .type(type).status(Notification.Status.UNREAD)
                .build());
        if (type == Notification.Type.REQUEST && patientOptedOutOfEmail(userId)) return; // in-app copy is kept
        userRepository.findById(userId).map(User::getEmail).ifPresent(email -> {
            try {
                emailService.send(email, title, message);
            } catch (Exception e) {
                log.warn("Email notification to {} failed: {}", email, e.getMessage());
            }
        });
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
