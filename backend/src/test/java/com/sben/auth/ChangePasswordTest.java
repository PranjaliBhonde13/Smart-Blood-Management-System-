package com.sben.auth;

import com.sben.auth.dto.AuthResponse;
import com.sben.auth.dto.ChangePasswordRequest;
import com.sben.auth.token.RefreshToken;
import com.sben.auth.token.RefreshTokenService;
import com.sben.auth.token.VerificationTokenRepository;
import com.sben.common.exception.FieldValidationException;
import com.sben.auth.device.LoginDeviceService;
import com.sben.notification.email.AccountEmails;
import com.sben.security.JwtService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangePasswordTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AccountEmails accountEmails;
    @Mock private LoginDeviceService loginDevices;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, verificationTokenRepository, refreshTokenService,
                encoder, jwtService, authenticationManager, accountEmails, loginDevices);
        user = User.builder().id(7L).email("user@sben.com").fullName("User").passwordHash(encoder.encode("Pass@123"))
                .role(Role.PATIENT).emailVerified(true).enabled(true).build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(7L)).thenReturn(RefreshToken.builder().token("new-refresh").build());
        when(jwtService.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn("new-access");
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void changePassword_WorksForEveryRole_RevokesOldSessionsAndReturnsFreshTokens(Role role) {
        user.setRole(role);

        AuthResponse res = authService.changePassword(7L, new ChangePasswordRequest("Pass@123", "N3w-Secret!"));

        assertTrue(encoder.matches("N3w-Secret!", user.getPasswordHash()));
        assertFalse(encoder.matches("Pass@123", user.getPasswordHash()));
        assertNotNull(user.getTokensValidAfter(), "previously issued access tokens are invalidated");
        // Old refresh tokens revoked before the new one is issued.
        var order = inOrder(refreshTokenService);
        order.verify(refreshTokenService).revokeAll(7L);
        order.verify(refreshTokenService).issue(7L);
        assertEquals("new-access", res.accessToken());
        assertEquals("new-refresh", res.refreshToken());
        verify(accountEmails).passwordChanged(eq(user), any(), eq(false));
    }

    @Test
    void changePassword_RejectsWrongCurrentPassword() {
        String before = user.getPasswordHash();
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> authService.changePassword(7L, new ChangePasswordRequest("wrong-pass", "N3w-Secret!")));
        assertTrue(ex.getFieldErrors().containsKey("currentPassword"));
        assertEquals(before, user.getPasswordHash());
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void changePassword_RejectsReusingTheCurrentPassword() {
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> authService.changePassword(7L, new ChangePasswordRequest("Pass@123", "Pass@123")));
        assertTrue(ex.getFieldErrors().containsKey("newPassword"));
    }

    @Test
    void changePassword_RejectedChange_QueuesNoEmail() {
        assertThrows(FieldValidationException.class,
                () -> authService.changePassword(7L, new ChangePasswordRequest("wrong-pass", "N3w-Secret!")));
        verifyNoInteractions(accountEmails);
    }
}
