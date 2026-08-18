package com.sben.auth;

import com.sben.auth.dto.*;
import com.sben.auth.token.RefreshToken;
import com.sben.auth.token.RefreshTokenService;
import com.sben.auth.token.VerificationToken;
import com.sben.auth.token.VerificationTokenRepository;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.notification.EmailService;
import com.sben.security.JwtService;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Orchestrates the full authentication lifecycle: registration, email OTP
 * verification, login, token refresh/rotation, logout, and password reset.
 *
 * <p>Security notes:
 * <ul>
 *   <li>Passwords are BCrypt-hashed; plaintext never persisted.</li>
 *   <li>Account inaccessible until email is verified.</li>
 *   <li>"Forgot password" never reveals whether an email exists (enumeration-safe).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.otp.expiration-minutes}") private int otpExpiryMinutes;
    @Value("${app.otp.length}")             private int otpLength;
    @Value("${app.jwt.access-expiration-ms}") private long accessExpirationMs;

    /** Register a user (disabled until OTP verified) and email an OTP. */
    @Transactional
    public void register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered", HttpStatus.CONFLICT);
        }
        User user = User.builder()
                .fullName(req.fullName())
                .email(req.email())
                .phone(req.phone())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(req.role())
                .emailVerified(false)
                .enabled(true)
                .build();
        userRepository.save(user);
        issueOtp(user, VerificationToken.Type.EMAIL_OTP);
        log.info("Registered user {} ({})", user.getEmail(), user.getRole());
    }

    /** Confirm the email OTP, activating the account. */
    @Transactional
    public void verifyOtp(VerifyOtpRequest req) {
        User user = getUserByEmail(req.email());
        VerificationToken token = validToken(user.getId(), VerificationToken.Type.EMAIL_OTP, req.otp());
        token.setUsed(true);
        verificationTokenRepository.save(token);
        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email verified for {}", user.getEmail());
    }

    /** Re-issue an email OTP (e.g. the previous one expired). */
    @Transactional
    public void resendOtp(EmailRequest req) {
        User user = getUserByEmail(req.email());
        if (user.isEmailVerified()) {
            throw new BusinessException("Email already verified");
        }
        issueOtp(user, VerificationToken.Type.EMAIL_OTP);
    }

    /** Authenticate and return a fresh token pair. */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (DisabledException e) {
            throw new BusinessException("Email not verified or account disabled", HttpStatus.FORBIDDEN);
        } catch (BadCredentialsException e) {
            throw new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }
        User user = getUserByEmail(req.email());
        return buildAuthResponse(user, refreshTokenService.issue(user.getId()).getToken());
    }

    /** Exchange a valid refresh token for a new token pair (rotation). */
    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken current = refreshTokenService.verify(req.refreshToken());
        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new BusinessException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        RefreshToken rotated = refreshTokenService.rotate(current);
        return buildAuthResponse(user, rotated.getToken());
    }

    /** Revoke all refresh tokens for the user (logout everywhere). */
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAll(userId);
    }

    /** Start password reset. Always succeeds outwardly to avoid email enumeration. */
    @Transactional
    public void forgotPassword(EmailRequest req) {
        userRepository.findByEmail(req.email())
                .ifPresent(u -> issueOtp(u, VerificationToken.Type.PASSWORD_RESET));
    }

    /** Complete password reset with a valid token. */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        User user = getUserByEmail(req.email());
        VerificationToken token =
                validToken(user.getId(), VerificationToken.Type.PASSWORD_RESET, req.token());
        token.setUsed(true);
        verificationTokenRepository.save(token);
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAll(user.getId()); // force re-login everywhere
    }

    public UserSummary currentUser(String email) {
        return UserSummary.from(getUserByEmail(email));
    }

    // ---------- helpers ----------

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        String access = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(access, refreshToken, "Bearer",
                accessExpirationMs / 1000, UserSummary.from(user));
    }

    private void issueOtp(User user, VerificationToken.Type type) {
        String code = generateNumericCode(otpLength);
        VerificationToken token = VerificationToken.builder()
                .userId(user.getId())
                .token(code)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .used(false)
                .build();
        verificationTokenRepository.save(token);
        emailService.sendOtp(user.getEmail(), code, otpExpiryMinutes);
    }

    private VerificationToken validToken(Long userId, VerificationToken.Type type, String code) {
        VerificationToken token = verificationTokenRepository
                .findFirstByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(userId, type)
                .orElseThrow(() -> new BusinessException("No active code — request a new one"));
        if (!token.isValid() || !token.getToken().equals(code)) {
            throw new BusinessException("Invalid or expired code");
        }
        return token;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }
}
