package com.sben.auth.dto;

import com.sben.user.Role;
import jakarta.validation.constraints.*;

/**
 * Registration payload. Bean Validation annotations produce 400 responses with
 * per-field messages via the global exception handler.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "invalid phone") String phone,
        @NotBlank @Size(min = 8, max = 72, message = "password must be 8-72 chars") String password,
        @NotNull Role role
) {}
