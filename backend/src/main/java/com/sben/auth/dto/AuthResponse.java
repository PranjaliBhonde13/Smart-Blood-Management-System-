package com.sben.auth.dto;

/** Returned on successful login/refresh: tokens plus a safe user summary. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSec,
        UserSummary user
) {}
