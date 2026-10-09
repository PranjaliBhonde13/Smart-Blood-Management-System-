package com.sben.notification.email;

import com.sben.common.exception.BusinessException;
import com.sben.notification.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailDispatcherTest {

    @Mock private EmailOutboxRepository repository;
    @Mock private EmailService transport;
    @Mock private PlatformTransactionManager txManager;

    private EmailDispatcher dispatcher;
    private EmailOutbox row;

    @BeforeEach
    void setUp() {
        dispatcher = new EmailDispatcher(repository, transport, txManager, 3, 20, 450, 25, 90);
        row = EmailOutbox.builder().id(1L).recipient("a@gmail.com").subject("S").htmlBody("<p>123456</p>")
                .textBody("123456").template("VERIFY_EMAIL").category(EmailCategory.SECURITY)
                .status(EmailStatus.SENDING).attempts(0).nextAttemptAt(LocalDateTime.now()).sensitive(true).build();
        when(repository.findById(1L)).thenReturn(Optional.of(row));
        when(repository.claim(eq(1L), any())).thenReturn(1);
        when(repository.countDispatchedSince(any())).thenReturn(0L);
    }

    @Test
    void successMarksSentAndWipesOneTimeCodes() {
        assertTrue(dispatcher.deliver(1L));
        verify(transport).deliver(argThat(e -> e.to().equals("a@gmail.com") && e.text().equals("123456")));
        assertEquals(EmailStatus.SENT, row.getStatus());
        assertNotNull(row.getSentAt());
        assertEquals(EmailDispatcher.REDACTED, row.getTextBody());
        assertEquals(EmailDispatcher.REDACTED, row.getHtmlBody());
    }

    @Test
    void temporaryFailureIsRetriedWithBackoff() {
        doThrow(new EmailDeliveryException("421 try later", false, null)).when(transport).deliver(any());
        dispatcher.deliver(1L);
        assertEquals(EmailStatus.PENDING, row.getStatus());
        assertTrue(row.getNextAttemptAt().isAfter(LocalDateTime.now().plusSeconds(30)), "waits before retrying");
        assertEquals("421 try later", row.getLastError());
        assertEquals("123456", row.getTextBody(), "kept for the retry");
    }

    @Test
    void permanentFailureStopsImmediately() {
        doThrow(new EmailDeliveryException("550 no such user", true, null)).when(transport).deliver(any());
        dispatcher.deliver(1L);
        assertEquals(EmailStatus.FAILED, row.getStatus());
        assertEquals(EmailDispatcher.REDACTED, row.getTextBody());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        row.setAttempts(2); // the claim makes this the 3rd attempt (max 3)
        doThrow(new RuntimeException("timeout")).when(transport).deliver(any());
        dispatcher.deliver(1L);
        assertEquals(EmailStatus.FAILED, row.getStatus());
    }

    @Test
    void emailClaimedElsewhereIsNotSentTwice() {
        when(repository.claim(eq(1L), any())).thenReturn(0);
        assertTrue(dispatcher.deliver(1L));
        verifyNoInteractions(transport);
    }

    @Test
    void rateLimitKeepsEmailQueued() {
        when(repository.countDispatchedSince(any())).thenReturn(20L);
        assertFalse(dispatcher.deliver(1L));
        verify(repository, never()).claim(any(), any());
        verifyNoInteractions(transport);
    }

    @Test
    void retryAllowedOnlyForFailedNonSensitiveEmails() {
        row.setStatus(EmailStatus.FAILED);
        assertThrows(BusinessException.class, () -> dispatcher.retry(1L), "one-time codes cannot be resent");

        row.setSensitive(false);
        row.setAttempts(6);
        dispatcher.retry(1L);
        assertEquals(EmailStatus.PENDING, row.getStatus());
        assertEquals(0, row.getAttempts());

        row.setStatus(EmailStatus.SENT);
        assertThrows(BusinessException.class, () -> dispatcher.retry(1L));
    }
}
