package com.sben.auth;

import com.sben.auth.dto.AuthResponse;
import com.sben.auth.dto.LoginRequest;
import com.sben.auth.token.RefreshToken;
import com.sben.auth.token.RefreshTokenService;
import com.sben.auth.token.VerificationTokenRepository;
import com.sben.notification.EmailService;
import com.sben.security.JwtService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRolesAuthTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                verificationTokenRepository,
                refreshTokenService,
                passwordEncoder,
                jwtService,
                authenticationManager,
                emailService
        );
    }

    @ParameterizedTest
    @CsvSource({
            "patient@sben.com, PATIENT",
            "donor@sben.com, DONOR",
            "hospital@sben.com, HOSPITAL",
            "bloodbank@sben.com, BLOOD_BANK",
            "admin@sben.com, ADMIN"
    })
    void login_ShouldSucceed_ForSeededRolesWithPass123(String email, String roleStr) {
        Role role = Role.valueOf(roleStr);
        User dummyUser = User.builder()
                .id(1L)
                .email(email)
                .fullName("Dummy " + role)
                .passwordHash("hashedPass123")
                .role(role)
                .emailVerified(true)
                .enabled(true)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(dummyUser));
        when(refreshTokenService.issue(1L)).thenReturn(RefreshToken.builder().token("mock-refresh").build());
        when(jwtService.generateAccessToken(eq(1L), eq(email), eq(role.name()))).thenReturn("mock-access-token");

        LoginRequest loginReq = new LoginRequest(email, "Pass@123");
        AuthResponse response = authService.login(loginReq);

        assertNotNull(response);
        assertEquals("mock-access-token", response.accessToken());
        assertEquals("mock-refresh", response.refreshToken());
        assertEquals(email, response.user().email());
        assertEquals(role.name(), response.user().role());
        verify(authenticationManager).authenticate(any());
    }
}
