package com.sben.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates stateless JWT access tokens. The secret and lifetimes are
 * externalised in application.yml (env-overridable). Refresh tokens are opaque
 * and tracked in the database (see {@link com.sben.auth.token.RefreshToken}).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessExpirationMs = accessExpirationMs;
    }

    /** Build a signed access token carrying the user's id and role. */
    public String generateAccessToken(Long userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                // iat_ms: millisecond issue time, compared against users.tokens_valid_after
                // (the standard iat claim only has second precision).
                .claims(Map.of("uid", userId, "role", role, "iat_ms", now.getTime()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    /** Issue time with millisecond precision (falls back to the standard iat claim). */
    public Date extractIssuedAt(String token) {
        Claims claims = parse(token);
        Number ms = claims.get("iat_ms", Number.class);
        return ms != null ? new Date(ms.longValue()) : claims.getIssuedAt();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
