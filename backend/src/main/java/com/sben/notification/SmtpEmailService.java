package com.sben.notification;

import com.sben.common.exception.BusinessException;
import com.sben.notification.email.EmailDeliveryException;
import com.sben.notification.email.OutgoingEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Production email adapter over SMTP (Gmail, Brevo or any SMTP relay). Active only when {@code app.mail-enabled=true}
 * and mail credentials are configured — otherwise {@link MockEmailService} is used.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail-enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String fromAddress;

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (fromAddress != null && !fromAddress.isBlank()) {
                msg.setFrom(fromAddress);
            }
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Plain-text email sent via SMTP to {}", to);
        } catch (MailException e) {
            log.error("Failed to send plain-text email via SMTP to {}: {}", to, e.getMessage(), e);
            throw new BusinessException("Failed to send email via SMTP: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("HTML email sent via SMTP to {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML email via SMTP to {}: {}", to, e.getMessage(), e);
            throw new BusinessException("Failed to send HTML email via SMTP: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends an outbox email as multipart/alternative (plain text + HTML) with
     * headers that mark it as automated, so mail clients don't auto-reply and
     * filters treat it as transactional mail.
     */
    @Override
    public void deliver(OutgoingEmail email) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(new InternetAddress(fromAddress, true));
            }
            helper.setTo(new InternetAddress(email.to(), true));
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mime.setHeader("Auto-Submitted", "auto-generated");
            mime.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");
            if (email.id() != null) mime.setHeader("X-SBEN-Email-Id", String.valueOf(email.id()));
            mailSender.send(mime);
            log.info("Email #{} delivered to SMTP server for {}", email.id(), email.to());
        } catch (AddressException | MailParseException e) {
            throw new EmailDeliveryException("Invalid email address: " + e.getMessage(), true, e);
        } catch (MailSendException e) {
            boolean badRecipient = e.getFailedMessages().values().stream().anyMatch(SmtpEmailService::isAddressRejection);
            throw new EmailDeliveryException(e.getMessage(), badRecipient, e);
        } catch (MailAuthenticationException e) {
            log.error("SMTP login failed. Check MAIL_USERNAME / MAIL_PASSWORD (for Gmail use an app password).");
            throw new EmailDeliveryException("SMTP authentication failed", false, e);
        } catch (MailException | MessagingException e) {
            throw new EmailDeliveryException(e.getMessage(), false, e);
        }
    }

    /** The server refused the recipient address itself (bad mailbox), so retrying cannot help. */
    private static boolean isAddressRejection(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AddressException) return true;
            if (t instanceof SendFailedException sfe
                    && sfe.getInvalidAddresses() != null && sfe.getInvalidAddresses().length > 0) return true;
        }
        return false;
    }
}

