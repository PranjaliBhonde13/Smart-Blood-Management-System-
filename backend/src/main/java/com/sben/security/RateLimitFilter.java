package com.sben.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sben.common.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight per-IP rate limiter for authentication endpoints, guarding against
 * brute-force and OTP/registration abuse. Uses an in-memory fixed-window counter
 * (no external dependency); for a multi-instance deployment swap the store for
 * Redis or Bucket4j — the filter contract stays the same.
 *
 * <p>Runs before the JWT filter and only inspects {@code /api/v1/auth/*}; all
 * other requests pass straight through.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;      // per window, per IP
    private static final long WINDOW_MS = 60_000;    // 1 minute
    private static final String PROTECTED_PREFIX = "/api/v1/auth/";
    private static final int EVICT_THRESHOLD = 10_000;
    /** Need a valid token already, so they are not brute-force targets; the SPA calls them routinely. */
    private static final Set<String> UNLIMITED = Set.of(
            "/api/v1/auth/refresh", "/api/v1/auth/me", "/api/v1/auth/logout");

    // Injected: Spring's ObjectMapper is pre-configured (e.g. Java-time support),
    // unlike a bare `new ObjectMapper()`.
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(PROTECTED_PREFIX) || UNLIMITED.contains(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        // Real client IP: with server.forward-headers-strategy=native, Tomcat's
        // RemoteIpValve resolves X-Forwarded-For from trusted proxies (nginx).
        String key = request.getRemoteAddr();
        if (!allow(key)) {
            writeTooManyRequests(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Fixed-window check: reset the counter each window, reject over the cap. */
    private boolean allow(String key) {
        long now = Instant.now().toEpochMilli();
        if (windows.size() > EVICT_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().start > WINDOW_MS);
        }
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start > WINDOW_MS) {
                return new Window(now);
            }
            return existing;
        });
        return w.count.incrementAndGet() <= MAX_REQUESTS;
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiError body = ApiError.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Too many attempts. Please wait a minute and try again.")
                .path(request.getRequestURI())
                .build();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** A counting window starting at {@code start} (epoch ms). */
    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long start) { this.start = start; }
    }
}
