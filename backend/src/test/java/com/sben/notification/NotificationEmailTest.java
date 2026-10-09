package com.sben.notification;

import com.sben.notification.email.EmailCategory;
import com.sben.notification.email.EmailContent;
import com.sben.notification.email.EmailOutboxService;
import com.sben.notification.email.EmailRenderer;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** In-app notifications are mirrored to email only for accounts that should receive it. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEmailTest {

    @Mock private NotificationRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private EmailOutboxService outbox;
    @Mock private PatientRepository patientRepository;

    private NotificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, userRepository, outbox,
                new EmailRenderer("App", "https://app.example.in"), patientRepository);
        user = User.builder().id(3L).email("d@gmail.com").fullName("Divya Rao").role(Role.DONOR)
                .enabled(true).emailVerified(true).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(patientRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void activeVerifiedUserGetsBrandedEmailWithLinkToTheRightPage() {
        service.notify(3L, "Blood needed: O+", "2 unit(s) of O+ needed.", Notification.Type.REQUEST);
        verify(repository).save(any(Notification.class));
        ArgumentCaptor<EmailContent> c = ArgumentCaptor.forClass(EmailContent.class);
        verify(outbox).enqueue(eq(3L), eq("d@gmail.com"), eq(EmailCategory.REQUEST), eq("NOTIFICATION_REQUEST"), c.capture(), eq(false));
        assertEquals("Blood needed: O+", c.getValue().subject());
        assertEquals("https://app.example.in/donor/open-requests", c.getValue().actionUrl());
        assertTrue(c.getValue().paragraphs().contains("Hi Divya,"));
    }

    @Test
    void suspendedOrUnverifiedUsersGetInAppOnly() {
        user.setEnabled(false);
        service.notify(3L, "t", "m", Notification.Type.DONATION);
        user.setEnabled(true);
        user.setEmailVerified(false);
        service.notify(3L, "t", "m", Notification.Type.DONATION);
        verify(repository, times(2)).save(any(Notification.class));
        verifyNoInteractions(outbox);
    }

    @Test
    void patientWhoOptedOutGetsNoRequestEmails() {
        when(patientRepository.findByUserId(3L)).thenReturn(Optional.of(Patient.builder().notificationConsent(false).build()));
        service.notify(3L, "t", "m", Notification.Type.REQUEST);
        verifyNoInteractions(outbox);
    }

    @Test
    void broadcastsUseTheAnnouncementCategory() {
        service.notify(3L, "Camp", "Donation camp on Sunday", Notification.Type.SYSTEM, EmailCategory.ANNOUNCEMENT);
        verify(outbox).enqueue(anyLong(), anyString(), eq(EmailCategory.ANNOUNCEMENT), anyString(), any(), eq(false));
    }

    @Test
    void everyRoleAndTypeHasALink() {
        for (Role r : Role.values()) {
            for (Notification.Type t : Notification.Type.values()) {
                assertTrue(NotificationService.actionPath(r, t).startsWith("/"), r + "/" + t);
            }
        }
    }
}
