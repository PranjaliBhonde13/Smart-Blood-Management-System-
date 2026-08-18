package com.sben.admin.dto;

import com.sben.user.User;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id, String fullName, String email, String phone, String role,
        boolean emailVerified, boolean enabled, LocalDateTime createdAt
) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole().name(), u.isEmailVerified(), u.isEnabled(), u.getCreatedAt());
    }
}
