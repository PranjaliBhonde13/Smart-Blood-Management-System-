package com.sben.config;

import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default administrator on first startup so the platform is manageable
 * out of the box. Credentials are configurable via env; the account is created
 * only if it does not already exist. Change the password after first login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@sben.com}")   private String adminEmail;
    @Value("${app.admin.password:Admin@123}")     private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail)) return;
        User admin = User.builder()
                .fullName("System Administrator")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .emailVerified(true)   // seeded accounts skip OTP
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Seeded default admin account: {} (change the password after first login)", adminEmail);
    }
}
