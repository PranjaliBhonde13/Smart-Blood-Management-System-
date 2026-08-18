package com.sben.auth.dto;

import com.sben.user.User;

/** Minimal, safe user projection returned to the client (never the entity). */
public record UserSummary(
        Long id,
        String fullName,
        String email,
        String role,
        boolean emailVerified
) {
    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getFullName(), u.getEmail(),
                u.getRole().name(), u.isEmailVerified());
    }
}
