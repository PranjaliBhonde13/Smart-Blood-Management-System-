package com.sben.config;

import com.sben.notification.email.AccountEmails;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * Creates the real administrator accounts listed in {@code ADMIN_EMAILS}
 * (comma-separated) at startup, independent of demo seeding.
 *
 * <p>No password is ever configured: a new account gets a random, unknown
 * password and an email explaining how to set one through "Forgot password"
 * (the reset code goes to that inbox, which proves ownership). Existing
 * accounts are never modified: an address already registered with another
 * role is reported and left alone rather than silently promoted.
 */
@Slf4j
@Component
public class AdminAccountProvisioner implements CommandLineRunner {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmails accountEmails;
    private final TransactionTemplate tx;
    private final String emails;

    public AdminAccountProvisioner(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   AccountEmails accountEmails, PlatformTransactionManager txManager,
                                   @Value("${app.admin.emails:}") String emails) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountEmails = accountEmails;
        this.tx = new TransactionTemplate(txManager);
        this.emails = emails;
    }

    @Override
    public void run(String... args) {
        Arrays.stream(emails.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .forEach(email -> tx.executeWithoutResult(s -> provision(email)));
    }

    void provision(String email) {
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            log.warn("ADMIN_EMAILS entry '{}' is not a valid email address; skipped", email);
            return;
        }
        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            if (existing.getRole() != Role.ADMIN) {
                log.warn("{} is already registered as {}; not changed to ADMIN. Use a different address "
                        + "or change the role deliberately.", email, existing.getRole());
            }
            return;
        }
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        User admin = userRepository.save(User.builder()
                .fullName("Administrator")
                .email(email)
                .passwordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)))
                .role(Role.ADMIN)
                .emailVerified(true) // the owner configured this address; "Forgot password" proves the inbox
                .enabled(true)
                .build());
        accountEmails.adminAccess(admin);
        log.info("Created administrator account {}. Set its password with 'Forgot password'.", email);
    }
}
