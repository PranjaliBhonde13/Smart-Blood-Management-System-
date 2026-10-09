package com.sben.patient.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload to create/update the caller's own patient profile. There are no
 * verification fields: a patient can never set their own verification status.
 * {@code fullName} and {@code phone} update the account ({@code users}).
 */
public record PatientRequest(
        // ---- personal ----
        @NotBlank(message = "Full name is required") @Size(max = 100, message = "Use 100 characters or fewer") String fullName,
        @NotNull(message = "Date of birth is required") @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        @NotBlank(message = "Blood group is required")
        @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Choose a valid blood group") String bloodGroup,
        @Pattern(regexp = "^$|^(MALE|FEMALE|OTHER|UNSPECIFIED)$", message = "Choose a valid option") String gender,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^(\\+91[\\- ]?)?[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,

        // ---- address & location ----
        @NotBlank(message = "Full address is required") @Size(max = 255) String address,
        @NotBlank(message = "City is required") @Size(max = 50) String city,
        @NotBlank(message = "State is required") @Size(max = 50) String state,
        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "PIN code must be 6 digits and cannot start with 0") String pincode,
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90") BigDecimal latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180") BigDecimal longitude,

        // ---- emergency contact & preferences (optional) ----
        @Size(max = 100, message = "Use 100 characters or fewer") String emergencyContactName,
        @Pattern(regexp = "^$|^(\\+91[\\- ]?)?[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number")
        String emergencyContactPhone,
        Boolean notificationConsent,
        Boolean privacyAcknowledged
) {}
