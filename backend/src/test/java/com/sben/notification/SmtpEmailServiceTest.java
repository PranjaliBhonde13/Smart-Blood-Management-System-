package com.sben.notification;

import com.sben.common.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private SmtpEmailService smtpEmailService;

    @Mock
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(smtpEmailService, "fromAddress", "Smart Blood Network <no-reply@sben.org>");
    }

    @Test
    void send_ShouldSendSimpleMailMessage() {
        smtpEmailService.send("user@example.com", "Test Subject", "Test Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMsg = captor.getValue();
        assertEquals("Smart Blood Network <no-reply@sben.org>", sentMsg.getFrom());
        assertEquals("user@example.com", sentMsg.getTo()[0]);
        assertEquals("Test Subject", sentMsg.getSubject());
        assertEquals("Test Body", sentMsg.getText());
    }

    @Test
    void send_ShouldThrowBusinessExceptionWhenMailExceptionOccurs() {
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(BusinessException.class, () ->
                smtpEmailService.send("user@example.com", "Subject", "Body"));
    }

    @Test
    void sendHtml_ShouldSendMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        smtpEmailService.sendHtml("user@example.com", "HTML Subject", "<h1>Hello</h1>");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
