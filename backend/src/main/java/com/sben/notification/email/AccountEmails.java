package com.sben.notification.email;

import com.sben.notification.email.EmailContent.Detail;
import com.sben.notification.email.EmailContent.Tone;
import com.sben.user.Role;
import com.sben.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Account and security emails: sign-up verification, password reset, welcome,
 * password changed, new sign-in, suspension/restoration and admin access.
 * Every method only queues the email (inside the caller's transaction);
 * {@link EmailDispatcher} delivers it after commit.
 */
@Component
public class AccountEmails {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a z");

    /** Where a finished email goes: the outbox normally, or a preview recipient. */
    @FunctionalInterface
    public interface Sink {
        void accept(User user, EmailCategory category, String template, boolean sensitive, EmailContent content);
    }

    private final EmailRenderer renderer;
    private final Sink sink;

    @Autowired
    public AccountEmails(EmailOutboxService outbox, EmailRenderer renderer) {
        this(renderer, (u, category, template, sensitive, content) ->
                outbox.enqueue(u.getId(), u.getEmail(), category, template, content, sensitive));
    }

    private AccountEmails(EmailRenderer renderer, Sink sink) {
        this.renderer = renderer;
        this.sink = sink;
    }

    /** Same templates, delivered through {@code sink} (used for template previews). */
    public AccountEmails withSink(Sink sink) {
        return new AccountEmails(renderer, sink);
    }

    public void verificationCode(User u, String code, int minutes) {
        queue(u, EmailCategory.SECURITY, "VERIFY_EMAIL", true, EmailContent.builder()
                .subject("Verify your email address")
                .preheader("Your verification code is inside. It expires in " + minutes + " minutes.")
                .heading("Confirm your email address")
                .paragraph(greeting(u) + " Use this code to verify your email and activate your account.")
                .code(code)
                .codeNote("Expires in " + minutes + " minutes")
                .afterAction("Never share this code. Our team will never ask you for it.")
                .afterAction("If you didn't create an account, you can ignore this email.")
                .build());
    }

    public void passwordResetCode(User u, String code, int minutes, String ip) {
        queue(u, EmailCategory.SECURITY, "PASSWORD_RESET_CODE", true, EmailContent.builder()
                .subject("Your password reset code")
                .preheader("Use this code to reset your password. It expires in " + minutes + " minutes.")
                .heading("Reset your password")
                .paragraph(greeting(u) + " We received a request to reset the password for your account. "
                        + "Enter this code on the reset page to choose a new password.")
                .code(code)
                .codeNote("Expires in " + minutes + " minutes")
                .detail(new Detail("Requested", now()))
                .detail(new Detail("From IP address", ip))
                .actionLabel("Open the reset page")
                .actionUrl(renderer.link("/reset-password"))
                .afterAction("Didn't ask for this? Ignore this email: your password stays the same. "
                        + "Never share this code with anyone.")
                .tone(Tone.ALERT)
                .build());
    }

    public void welcome(User u) {
        queue(u, EmailCategory.ACCOUNT, "WELCOME", false, EmailContent.builder()
                .subject("Welcome to Smart Blood Emergency Network")
                .preheader("Your account is active. Here is what to do next.")
                .heading("Welcome aboard, " + firstName(u) + "!")
                .paragraph("Your email is verified and your " + roleLabel(u.getRole()) + " account is active.")
                .paragraph(nextSteps(u.getRole()))
                .actionLabel("Sign in")
                .actionUrl(renderer.link("/login"))
                .tone(Tone.SUCCESS)
                .build());
    }

    public void passwordChanged(User u, String ip, boolean viaReset) {
        queue(u, EmailCategory.SECURITY, "PASSWORD_CHANGED", false, EmailContent.builder()
                .subject("Your password was changed")
                .preheader("The password for your account was just changed.")
                .heading("Your password was changed")
                .paragraph(greeting(u) + " The password for your account was "
                        + (viaReset ? "reset using a code sent to this email." : "changed from your account settings.")
                        + " For your security, you have been signed out of every other device.")
                .detail(new Detail("When", now()))
                .detail(new Detail("IP address", ip))
                .actionLabel("This wasn't me — reset my password")
                .actionUrl(renderer.link("/forgot-password"))
                .afterAction("If you made this change, no action is needed.")
                .tone(Tone.ALERT)
                .build());
    }

    public void newSignIn(User u, String ip, String device) {
        queue(u, EmailCategory.SECURITY, "NEW_SIGN_IN", false, EmailContent.builder()
                .subject("New sign-in to your account")
                .preheader("We noticed a sign-in from a new device or network.")
                .heading("New sign-in detected")
                .paragraph(greeting(u) + " Your account was just signed in to from a device or network we haven't seen before.")
                .detail(new Detail("When", now()))
                .detail(new Detail("Device", device))
                .detail(new Detail("IP address", ip))
                .actionLabel("This wasn't me — secure my account")
                .actionUrl(renderer.link("/forgot-password"))
                .afterAction("If this was you, no action is needed.")
                .tone(Tone.ALERT)
                .build());
    }

    public void accountSuspended(User u, String reason) {
        queue(u, EmailCategory.ACCOUNT, "ACCOUNT_SUSPENDED", false, EmailContent.builder()
                .subject("Your account has been suspended")
                .preheader("You can't sign in until an administrator restores access.")
                .heading("Your account has been suspended")
                .paragraph(greeting(u) + " An administrator has suspended your account. You won't be able to sign in "
                        + "until access is restored.")
                .detail(new Detail("Reason", reason == null || reason.isBlank() ? "Not specified" : reason))
                .afterAction("If you think this is a mistake, reply to your administrator or contact support.")
                .tone(Tone.ALERT)
                .build());
    }

    public void accountRestored(User u) {
        queue(u, EmailCategory.ACCOUNT, "ACCOUNT_RESTORED", false, EmailContent.builder()
                .subject("Your account has been restored")
                .preheader("You can sign in again.")
                .heading("Welcome back")
                .paragraph(greeting(u) + " An administrator has restored your account. You can sign in again.")
                .actionLabel("Sign in")
                .actionUrl(renderer.link("/login"))
                .tone(Tone.SUCCESS)
                .build());
    }

    public void adminAccess(User u) {
        queue(u, EmailCategory.SECURITY, "ADMIN_ACCESS", false, EmailContent.builder()
                .subject("You have administrator access")
                .preheader("Set your password to start using the admin console.")
                .heading("Administrator access granted")
                .paragraph(greeting(u) + " An administrator account has been set up for this email address.")
                .paragraph("To sign in for the first time, choose a password: open \"Forgot password\", enter this email "
                        + "address, and use the code we send you.")
                .actionLabel("Set my password")
                .actionUrl(renderer.link("/forgot-password"))
                .afterAction("If you didn't expect this, contact your system owner. Without a password the account can't be used.")
                .build());
    }

    // ---------- helpers ----------

    private void queue(User u, EmailCategory category, String template, boolean sensitive, EmailContent content) {
        sink.accept(u, category, template, sensitive, content);
    }

    static String greeting(User u) {
        return "Hi " + firstName(u) + ",";
    }

    static String firstName(User u) {
        String name = u.getFullName() == null ? "" : u.getFullName().trim();
        return name.isEmpty() ? "there" : name.split("\\s+")[0];
    }

    private static String now() {
        return ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault()).format(WHEN);
    }

    static String roleLabel(Role role) {
        return switch (role) {
            case PATIENT -> "patient";
            case DONOR -> "donor";
            case HOSPITAL -> "hospital";
            case BLOOD_BANK -> "blood bank";
            case ADMIN -> "administrator";
        };
    }

    private static String nextSteps(Role role) {
        return switch (role) {
            case DONOR -> "Next, complete your donor profile (blood group, location and availability) so we can alert you "
                    + "when someone nearby needs blood you can give.";
            case PATIENT -> "Next, complete your profile and verify your blood group so you can raise blood requests quickly "
                    + "in an emergency.";
            case HOSPITAL -> "Next, complete your hospital profile and upload your registration documents. An administrator "
                    + "reviews them before you can verify and approve blood requests.";
            case BLOOD_BANK -> "Next, complete your blood bank profile and upload your license. Once an administrator verifies it, "
                    + "you can manage stock and fulfil requests.";
            case ADMIN -> "You can manage users, verifications and requests from the admin console.";
        };
    }
}
