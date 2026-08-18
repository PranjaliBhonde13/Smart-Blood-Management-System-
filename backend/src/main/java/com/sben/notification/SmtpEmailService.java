package com.sben.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Production email adapter over SMTP. Active only when {@code app.mail-enabled=true}
 * and mail credentials are configured — otherwise {@link MockEmailService} is used.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail-enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        log.info("Email sent to {}", to);
    }
}
