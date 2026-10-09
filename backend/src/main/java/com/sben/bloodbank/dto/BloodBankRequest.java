package com.sben.bloodbank.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Blood bank registration form. Verification fields are deliberately absent:
 * the owner can never set their own verification status. The license document
 * and logo are uploaded through their own multipart endpoints.
 */
public record BloodBankRequest(
        // ---- basic + contact ----
        @NotBlank(message = "Blood bank name is required") @Size(max = 100) String bankName,
        @NotBlank(message = "Official contact email is required")
        @Email(message = "Enter a valid email address") @Size(max = 150) String contactEmail,
        @NotBlank(message = "Official contact phone is required")
        @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "Enter a valid phone number (7–15 digits)") String phone,
        @Pattern(regexp = "^$|^[0-9+\\- ]{7,15}$", message = "Enter a valid phone number (7–15 digits)") String alternatePhone,
        @Size(max = 255)
        @Pattern(regexp = "^$|^https?://[^\\s]+\\.[^\\s]+$", message = "Website must start with http:// or https://") String website,

        // ---- address & location ----
        @NotBlank(message = "Full address is required") @Size(max = 255) String address,
        @NotBlank(message = "State is required") @Size(max = 50) String state,
        @NotBlank(message = "City is required") @Size(max = 50) String city,
        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "PIN code must be 6 digits and cannot start with 0") String pincode,
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90") BigDecimal latitude,
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180") BigDecimal longitude,

        // ---- license ----
        @NotBlank(message = "License number is required") @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9/\\-. ]+$", message = "License number may contain letters, digits, / - . and spaces")
        String licenseNumber,
        @PastOrPresent(message = "Issue date cannot be in the future") LocalDate licenseIssueDate,
        LocalDate licenseExpiryDate
) {}
