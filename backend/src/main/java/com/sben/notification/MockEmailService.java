package com.sben.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Dev/test email adapter. Instead of sending, it logs the message so the OTP is
 * visible in the console during development — no SMTP account required.
 *
 * <p>Active when {@code app.mail-enabled=false} (the default). The real SMTP
 * adapter takes over when mail is enabled.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail-enabled", havingValue = "false", matchIfMissing = true)
public class MockEmailService implements EmailService {

    @Override
    public void send(String to, String subject, String body) {
        log.info("""

                ┌──────────────── MOCK EMAIL ────────────────
                │ To     : {}
                │ Subject: {}
                │ Body   : {}
                └─────────────────────────────────────────────""", to, subject, body);
    }
}
