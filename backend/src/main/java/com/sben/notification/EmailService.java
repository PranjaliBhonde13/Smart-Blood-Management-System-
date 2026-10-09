package com.sben.notification;

/**
 * Port for outbound email. Implementations are swappable adapters:
 * {@link MockEmailService} (dev, logs) and an SMTP adapter (prod, added later).
 * Callers depend on this interface only — Dependency Inversion.
 */
public interface EmailService {

    /** Send a plain-text email. */
    void send(String to, String subject, String body);

    /** Send an HTML email (falls back to plain-text send if unimplemented). */
    default void sendHtml(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody);
    }

    /**
     * Deliver a rendered outbox email (HTML with a plain-text alternative).
     * Used by the email dispatcher; throws {@link com.sben.notification.email.EmailDeliveryException}
     * when the server rejects it, marking whether a retry makes sense.
     */
    default void deliver(com.sben.notification.email.OutgoingEmail email) {
        sendHtml(email.to(), email.subject(), email.html());
    }

    /** Convenience for the OTP flow sending a styled HTML email with plain text fallback. */
    default void sendOtp(String to, String code, int expiryMinutes) {
        String subject = "Your Verification Code — Smart Blood Emergency Network";
        String htmlBody = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; margin: 0; padding: 0; }
                    .container { max-width: 560px; margin: 30px auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.08); }
                    .header { background: linear-gradient(135deg, #e63946 0%%, #b71c1c 100%%); padding: 30px; text-align: center; color: #ffffff; }
                    .header h1 { margin: 0; font-size: 22px; font-weight: 700; letter-spacing: 0.5px; }
                    .header p { margin: 5px 0 0 0; font-size: 13px; opacity: 0.9; }
                    .content { padding: 35px 30px; text-align: center; color: #2b2d42; }
                    .otp-card { background: #f8f9fa; border: 2px dashed #e63946; border-radius: 10px; padding: 20px; margin: 20px 0; }
                    .otp-code { font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #e63946; font-family: monospace; margin: 5px 0; }
                    .expiry { font-size: 13px; color: #6c757d; margin-top: 8px; }
                    .footer { background: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #8d99ae; border-top: 1px solid #edf2f4; }
                    .warning { font-size: 12px; color: #d90429; margin-top: 15px; }
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="header">
                      <h1>Smart Blood Emergency Network</h1>
                      <p>Verification & Security Service</p>
                    </div>
                    <div class="content">
                      <h2 style="margin-top: 0;">Verification Code</h2>
                      <p>Use the following 6-digit OTP code to complete your verification.</p>
                      <div class="otp-card">
                        <div class="otp-code">%s</div>
                        <div class="expiry">Valid for <strong>%d minutes</strong></div>
                      </div>
                      <p class="warning">⚠️ If you did not request this code, please ignore this email.</p>
                    </div>
                    <div class="footer">
                      <p>&copy; Smart Blood Emergency Network. Saving lives together.</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(code, expiryMinutes);

        sendHtml(to, subject, htmlBody);
    }
}

