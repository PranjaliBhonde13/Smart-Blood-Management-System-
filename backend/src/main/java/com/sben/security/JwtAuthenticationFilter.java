package com.sben.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Runs once per request: extracts a Bearer token, validates it, and populates
 * the SecurityContext so downstream {@code @PreAuthorize} checks and controllers
 * see an authenticated principal. Requests without a valid token pass through
 * unauthenticated and are rejected later by the authorization rules.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = loadActiveUser(token);
                if (userDetails != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * The token's user, or null when the account no longer exists, is
     * suspended/deleted, or revoked its tokens after this one was issued
     * (logout, password reset, suspension). A null leaves the request
     * unauthenticated, so protected routes answer 401.
     */
    private UserDetails loadActiveUser(String token) {
        UserPrincipal principal;
        try {
            principal = (UserPrincipal) userDetailsService.loadUserByUsername(jwtService.extractEmail(token));
        } catch (UsernameNotFoundException e) {
            return null;
        }
        if (!principal.isEnabled() || principal.user().isDeleted()) return null;
        LocalDateTime validAfter = principal.user().getTokensValidAfter();
        if (validAfter != null) {
            LocalDateTime issuedAt = LocalDateTime.ofInstant(
                    jwtService.extractIssuedAt(token).toInstant(), ZoneId.systemDefault());
            if (issuedAt.isBefore(validAfter)) return null;
        }
        return principal;
    }
}
