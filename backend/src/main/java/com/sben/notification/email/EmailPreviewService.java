package com.sben.notification.email;

import com.sben.common.exception.BusinessException;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Sends one sample of every email template to an administrator's inbox so the
 * designs can be reviewed in real mail clients. Samples go through the same
 * template code and outbox as real emails; subjects are prefixed "[Preview]"
 * and all names, codes and details are sample data. Only administrator
 * accounts can receive previews, so this can't be used to email anyone else.
 */
@Service
@RequiredArgsConstructor
public class EmailPreviewService {

    static final String PREFIX = "[Preview] ";

    private final UserRepository userRepository;
    private final AccountEmails accountEmails;
    private final NotificationService notificationService;
    private final EmailOutboxService outbox;

    /** Queues every template for {@code adminEmail}; returns how many were queued. */
    @Transactional
    public int sendAll(String adminEmail) {
        String email = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
        User admin = userRepository.findByEmail(email)
                .filter(u -> u.getRole() == Role.ADMIN && u.isEnabled() && !u.isDeleted())
                .orElseThrow(() -> new BusinessException(
                        "Previews can only be sent to an active administrator account", HttpStatus.BAD_REQUEST));

        int[] count = {0};
        AccountEmails.Sink toAdmin = (sample, category, template, sensitive, content) -> {
            outbox.enqueue(admin.getId(), admin.getEmail(), category, "PREVIEW_" + template,
                    content.toBuilder().subject(PREFIX + content.subject()).build(), false);
            count[0]++;
        };
        AccountEmails previews = accountEmails.withSink(toAdmin);

        User donor = sample(Role.DONOR);
        previews.verificationCode(donor, "482915", 10);
        previews.passwordResetCode(donor, "730164", 10, "49.36.112.27");
        previews.welcome(donor);
        previews.passwordChanged(donor, "49.36.112.27", true);
        previews.newSignIn(donor, "106.51.73.18", "Chrome on Android");
        previews.accountSuspended(donor, "Multiple reports of incorrect contact details. Please contact support to update them.");
        previews.accountRestored(donor);
        previews.adminAccess(sample(Role.ADMIN));

        notification(toAdmin, sample(Role.DONOR), "Blood needed: O+",
                "2 unit(s) of O+ are needed (critical urgency) at Sanjeevani Multispeciality Hospital, Andheri West. "
                        + "Open Requests has the details — request #1042.",
                Notification.Type.REQUEST, EmailCategory.REQUEST);
        notification(toAdmin, sample(Role.PATIENT), "Your request is fulfilled",
                "Request #1042 has been completed. Thank you for trusting Smart Blood Emergency Network.",
                Notification.Type.REQUEST, EmailCategory.REQUEST);
        notification(toAdmin, sample(Role.DONOR), "Thank you for donating",
                "Your donation for request #1042 has been recorded. You can donate again from 8 January 2027.",
                Notification.Type.DONATION, EmailCategory.DONATION);
        notification(toAdmin, sample(Role.HOSPITAL), "Hospital verified",
                "Sanjeevani Multispeciality Hospital is now verified. You can verify, approve and complete blood requests.",
                Notification.Type.VERIFICATION, EmailCategory.VERIFICATION);
        notification(toAdmin, sample(Role.DONOR), "Blood donation camp this Sunday",
                "Join us at Shivaji Park, Dadar on Sunday 18 October, 9 am – 4 pm. Walk-ins welcome; please bring a photo ID.",
                Notification.Type.SYSTEM, EmailCategory.ANNOUNCEMENT);
        return count[0];
    }

    private void notification(AccountEmails.Sink sink, User sample, String title, String message,
                              Notification.Type type, EmailCategory category) {
        sink.accept(sample, category, "NOTIFICATION_" + type.name(), false,
                notificationService.notificationEmail(sample, title, message, type));
    }

    /** A fictional recipient, so previews show realistic personalised content. */
    private static User sample(Role role) {
        return User.builder().fullName("Asha Sharma").email("asha.sharma@example.com").role(role).build();
    }
}
