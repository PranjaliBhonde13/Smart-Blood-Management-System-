package com.sben.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Single-email payload used by resend-OTP and forgot-password. */
public record EmailRequest(
        @NotBlank @Email String email
) {}
