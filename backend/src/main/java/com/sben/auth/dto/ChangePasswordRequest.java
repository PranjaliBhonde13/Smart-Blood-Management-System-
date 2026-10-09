package com.sben.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Signed-in user changes their own password; the current one must be supplied. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank @Size(min = 8, max = 72, message = "password must be 8-72 chars") String newPassword
) {}
