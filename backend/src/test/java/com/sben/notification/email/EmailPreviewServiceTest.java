package com.sben.notification.email;

import com.sben.common.exception.BusinessException;
import com.sben.notification.NotificationRepository;
import com.sben.notification.NotificationService;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailPreviewServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailOutboxService outbox;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PatientRepository patientRepository;

    private EmailPreviewService service;

    @BeforeEach
    void setUp() {
        EmailRenderer renderer = new EmailRenderer("App", "http://localhost:5173");
        service = new EmailPreviewService(userRepository, new AccountEmails(outbox, renderer),
                new NotificationService(notificationRepository, userRepository, outbox, renderer, patientRepository), outbox);
        when(userRepository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(
                User.builder().id(9L).email("admin@gmail.com").role(Role.ADMIN).enabled(true).build()));
        when(userRepository.findByEmail("donor@gmail.com")).thenReturn(Optional.of(
                User.builder().id(3L).email("donor@gmail.com").role(Role.DONOR).enabled(true).build()));
    }

    @Test
    void sendsEveryTemplateOnlyToTheAdminWithPreviewSubjects() {
        int n = service.sendAll(" Admin@Gmail.com ");
        assertEquals(13, n);
        ArgumentCaptor<EmailContent> content = ArgumentCaptor.forClass(EmailContent.class);
        verify(outbox, times(13)).enqueue(eq(9L), eq("admin@gmail.com"), any(), startsWith("PREVIEW_"), content.capture(), eq(false));
        assertTrue(content.getAllValues().stream().allMatch(c -> c.subject().startsWith("[Preview] ")));
        verifyNoInteractions(notificationRepository); // previews create no in-app notifications
    }

    @Test
    void refusesNonAdminOrUnknownRecipients() {
        assertThrows(BusinessException.class, () -> service.sendAll("donor@gmail.com"));
        assertThrows(BusinessException.class, () -> service.sendAll("stranger@gmail.com"));
        verifyNoInteractions(outbox);
    }
}
