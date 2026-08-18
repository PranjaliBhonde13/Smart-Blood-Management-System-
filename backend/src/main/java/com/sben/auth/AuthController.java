package com.sben.auth;

import com.sben.auth.dto.*;
import com.sben.common.response.ApiResponse;
import com.sben.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication & identity endpoints (see docs/04-API.md §2). All routes here
 * are public except {@code /logout} and {@code /me}, which require a valid JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, OTP, tokens, password reset")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new account and send an email OTP")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registered. OTP sent to email.", null));
    }

    @Operation(summary = "Verify email with the OTP")
    @PostMapping("/verify-otp")
    public ApiResponse<Void> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        authService.verifyOtp(req);
        return ApiResponse.ok("Email verified. You can now log in.", null);
    }

    @Operation(summary = "Resend the email OTP")
    @PostMapping("/resend-otp")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody EmailRequest req) {
        authService.resendOtp(req);
        return ApiResponse.ok("OTP resent.", null);
    }

    @Operation(summary = "Log in and receive access + refresh tokens")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok("Login successful", authService.login(req));
    }

    @Operation(summary = "Rotate tokens using a valid refresh token")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok("Token refreshed", authService.refresh(req));
    }

    @Operation(summary = "Revoke all refresh tokens for the current user")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.getId());
        return ApiResponse.ok("Logged out.", null);
    }

    @Operation(summary = "Request a password-reset code")
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest req) {
        authService.forgotPassword(req);
        // Always OK — do not reveal whether the email exists.
        return ApiResponse.ok("If the email exists, a reset code has been sent.", null);
    }

    @Operation(summary = "Reset password with a valid code")
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.ok("Password updated. Please log in.", null);
    }

    @Operation(summary = "Get the currently authenticated user")
    @GetMapping("/me")
    public ApiResponse<UserSummary> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(authService.currentUser(principal.getUsername()));
    }
}
