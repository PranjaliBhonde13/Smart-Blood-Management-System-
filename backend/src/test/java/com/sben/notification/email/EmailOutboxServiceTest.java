package com.sben.notification.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

    @Mock private EmailOutboxRepository repository;
    @Mock private ApplicationEventPublisher events;

    private EmailOutboxService service;
    private final EmailContent content = EmailContent.builder().subject("Subject").code("654321").build();

    @BeforeEach
    void setUp() {
        service = new EmailOutboxService(repository, new EmailRenderer("App", "http://localhost:5173"), events,
                "example.com, sben.com");
        lenient().when(repository.save(any(EmailOutbox.class))).thenAnswer(inv -> {
            EmailOutbox e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });
    }

    private EmailOutbox saved() {
        ArgumentCaptor<EmailOutbox> c = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(repository).save(c.capture());
        return c.getValue();
    }

    @Test
    void realAddressIsQueuedAndPublishedForImmediateDelivery() {
        service.enqueue(7L, "  Person@Gmail.com ", EmailCategory.SECURITY, "VERIFY_EMAIL", content, true);
        EmailOutbox e = saved();
        assertEquals("person@gmail.com", e.getRecipient());
        assertEquals(EmailStatus.PENDING, e.getStatus());
        assertEquals(0, e.getPriority(), "security emails jump the queue");
        assertTrue(e.getTextBody().contains("654321"));
        verify(events).publishEvent(new EmailOutboxService.QueuedEvent(42L));
    }

    @Test
    void demoAndTestDomainsAreNeverSent() {
        service.enqueue(1L, "admin@sben.com", EmailCategory.SECURITY, "VERIFY_EMAIL", content, true);
        EmailOutbox e = saved();
        assertEquals(EmailStatus.SUPPRESSED, e.getStatus());
        assertFalse(e.getTextBody().contains("654321"), "codes for suppressed addresses are not stored");
        verifyNoInteractions(events);
    }

    @Test
    void subdomainsOfSuppressedDomainsAndInvalidAddressesAreSuppressed() {
        assertNotNull(service.suppressionReason("a@mail.example.com"));
        assertNotNull(service.suppressionReason("not-an-email"));
        assertNull(service.suppressionReason("a@notexample.com"));
    }
}
