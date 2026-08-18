package com.sben.notification;

/**
 * Port for outbound email. Implementations are swappable adapters:
 * {@link MockEmailService} (dev, logs) and an SMTP adapter (prod, added later).
 * Callers depend on this interface only — Dependency Inversion.
 */
public interface EmailService {

    /** Send a plain-text email. */
    void send(String to, String subject, String body);

    /** Convenience for the OTP flow. */
    default void sendOtp(String to, String code, int expiryMinutes) {
        send(to, "Your verification code",
                "Your Smart Blood Emergency Network code is: %s (valid %d minutes)."
                        .formatted(code, expiryMinutes));
    }
}
