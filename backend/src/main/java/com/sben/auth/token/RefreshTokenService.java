package com.sben.auth.token;

import com.sben.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Issues, validates, rotates, and revokes opaque refresh tokens. Access tokens
 * are short-lived JWTs; a valid refresh token exchanges for a fresh pair.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /** Create and persist a new refresh token for a user. */
    public RefreshToken issue(Long userId) {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .token(UUID.randomUUID().toString() + UUID.randomUUID())
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000))
                .revoked(false)
                .build();
        return repository.save(token);
    }

    /** Validate an incoming refresh token, throwing 401 if unusable. */
    public RefreshToken verify(String token) {
        RefreshToken stored = repository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Invalid refresh token", HttpStatus.UNAUTHORIZED));
        if (!stored.isActive()) {
            throw new BusinessException("Refresh token expired or revoked", HttpStatus.UNAUTHORIZED);
        }
        return stored;
    }

    /** Rotate: revoke the presented token and issue a replacement. */
    public RefreshToken rotate(RefreshToken current) {
        current.setRevoked(true);
        repository.save(current);
        return issue(current.getUserId());
    }

    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId);
    }
}
