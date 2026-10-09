# Brevo SMTP OTP Email Implementation Plan

This document describes the minimal changes needed to send existing email OTPs through Brevo SMTP in the current Spring Boot backend.

No Nodemailer service is required for this project because the backend is Java Spring Boot and already has `spring-boot-starter-mail`, `EmailService`, `SmtpEmailService`, and OTP endpoints.

## Goal

Use Brevo SMTP to send OTP emails for:

- User registration email verification.
- Existing resend OTP flow.
- Existing password reset OTP flow.

The first implementation should keep the current OTP flow unchanged and only replace development mock email logging with real SMTP sending when enabled by environment variables.

## Existing Code Already Available

- Backend OTP generation: `backend/src/main/java/com/sben/auth/AuthService.java`
- OTP API endpoints: `backend/src/main/java/com/sben/auth/AuthController.java`
- Email abstraction: `backend/src/main/java/com/sben/notification/EmailService.java`
- SMTP adapter: `backend/src/main/java/com/sben/notification/SmtpEmailService.java`
- Development mock email adapter: `backend/src/main/java/com/sben/notification/MockEmailService.java`
- Mail and OTP config: `backend/src/main/resources/application.yml`
- OTP verification page: `frontend/src/features/auth/pages/VerifyOtpPage.jsx`
- Frontend auth API calls: `frontend/src/features/auth/authApi.js`

## Minimal Files To Change After Approval

### 1. `backend/src/main/resources/application.yml`

Move the `mail` configuration under `spring`, because Spring Boot reads SMTP settings from `spring.mail`.

Set Brevo as the default SMTP host:

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp-relay.brevo.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

Add application-level sender config:

```yaml
app:
  mail:
    from: "${MAIL_FROM:Smart Blood Emergency Network <pranjalibhonde13@gmail.com>}"
  mail-enabled: ${MAIL_ENABLED:false}
```

`MAIL_ENABLED=false` keeps local development safe by using `MockEmailService`.

`MAIL_ENABLED=true` enables real Brevo email through `SmtpEmailService`.

### 2. `backend/src/main/java/com/sben/notification/SmtpEmailService.java`

Add a configured sender address to outgoing emails.

Reason: Brevo often requires the `from` address to match a verified sender/domain in the Brevo account.

Planned behavior:

- Read `app.mail.from`.
- If configured, set it as `msg.setFrom(...)`.
- Keep subject/body/to behavior unchanged.

### 3. `README.md`

Add a small Brevo setup section showing the required environment variables.

PowerShell example:

```powershell
$env:MAIL_ENABLED="true"
$env:MAIL_HOST="smtp-relay.brevo.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="pranjalibhonde13@gmail.com"
$env:MAIL_PASSWORD="your-brevo-smtp-key"
$env:MAIL_FROM="Smart Blood Emergency Network <pranjalibhonde13@gmail.com>"
./mvnw spring-boot:run
```

Important Brevo notes:

- Use the Brevo SMTP login, not necessarily your normal Brevo account email.
- Use a Brevo SMTP key, not the REST API key.
- Use a verified sender email/domain in `MAIL_FROM`.
- Port `587` uses STARTTLS.

### 4. Optional: `docker-compose.yml`

For local Docker testing with real Brevo SMTP, add these environment variables to the `backend` service:

```yaml
MAIL_ENABLED: "true"
MAIL_HOST: smtp-relay.brevo.com
MAIL_PORT: "587"
MAIL_USERNAME: ${MAIL_USERNAME}
MAIL_PASSWORD: ${MAIL_PASSWORD}
MAIL_FROM: ${MAIL_FROM}
```

This is optional because the current Docker setup uses MailHog for local email testing.

## What Will Not Change In The Minimal Implementation

- No frontend changes.
- No OTP endpoint changes.
- No database changes.
- No Nodemailer service.
- No JWT/login behavior changes.
- No changes to OTP expiration or resend logic.

The current registration flow already redirects users to `/verify-otp`.

## Environment Variables Required

```env
MAIL_ENABLED=true
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=pranjalibhonde13@gmail.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_FROM=Smart Blood Emergency Network <pranjalibhonde13@gmail.com>
```

Existing variables still apply:

```env
DB_USERNAME=root
DB_PASSWORD=root
JWT_SECRET=your-base64-secret
```

## Brevo Account Setup Required

1. Create or log in to a Brevo account.
2. Go to SMTP/API settings.
3. Enable transactional SMTP if required.
4. Copy the SMTP login.
5. Create or copy an SMTP key.
6. Create and verify a sender email address.
7. Use that verified sender in `MAIL_FROM`.

## Test Plan After Implementation

1. Start backend with `MAIL_ENABLED=false`.
2. Register a user and confirm OTP still appears in backend logs.
3. Start backend with Brevo env vars and `MAIL_ENABLED=true`.
4. Register a new user.
5. Confirm OTP arrives in email inbox.
6. Submit OTP on `/verify-otp`.
7. Confirm login works after verification.
8. Test `/resend-otp`.
9. Test `/forgot-password` and `/reset-password`.

## Recommended Later Security Improvements

These are not part of the minimal Brevo SMTP setup, but should be considered later:

- Store hashed OTP values instead of plaintext OTPs.
- Add max failed OTP attempts per token.
- Add resend cooldown and max resend count.
- Add cleanup job for expired OTPs.
- Replace in-memory rate limiting with Redis-backed rate limiting for production.
