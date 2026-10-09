package com.sben.hospital.dto;

import com.sben.hospital.HospitalCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Hospital profile form. Verification fields are deliberately absent: the
 * owner can never set their own verification or document status.
 *
 * <p>Required fields match what verification needs
 * (HospitalVerificationService.missingProfileFields) plus the blood bank
 * answer, which decides whether a blood bank license applies. Coordinates are
 * optional but must come as a pair (checked in HospitalService).
 */
public record HospitalRequest(
        @NotBlank @Size(max = 100) String hospitalName,
        @NotBlank @Size(max = 50) String licenseNumber,
        @Pattern(regexp = PHONE, message = PHONE_MESSAGE) @NotBlank String phone,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 50) String city,
        @NotBlank @Size(max = 50) String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "PIN code must be 6 digits") String pincode,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        /** Decides which document requirements apply. */
        @NotNull(message = "choose your registration category") HospitalCategory registrationCategory,
        /** True only if the hospital runs its own blood bank (then a blood bank license is required). */
        @NotNull(message = "tell us whether your hospital runs its own blood bank") Boolean operatesBloodBank,
        // --- contacts for coordinating blood (optional) ---
        @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String emergencyPhone,
        @Email @Size(max = 150) String officialEmail,
        @Size(max = 100) String contactPersonName,
        @Size(max = 100) String contactPersonDesignation
) {
    /** Indian landline (with STD code) or mobile: 10 digits, optionally prefixed with +91, 91 or 0. */
    public static final String PHONE = "^(?:\\+?91[\\s-]?|0)?[1-9](?:[\\s-]?\\d){9}$";
    static final String PHONE_MESSAGE = "enter a 10-digit number, including the STD code for landlines";

    /** Pre-document form shape, kept for existing callers. */
    public HospitalRequest(String hospitalName, String licenseNumber, String phone, String address, String city,
                           String state, String pincode, BigDecimal latitude, BigDecimal longitude) {
        this(hospitalName, licenseNumber, phone, address, city, state, pincode, latitude, longitude, null, null);
    }

    /** Pre-contacts form shape, kept for existing callers. */
    public HospitalRequest(String hospitalName, String licenseNumber, String phone, String address, String city,
                           String state, String pincode, BigDecimal latitude, BigDecimal longitude,
                           HospitalCategory registrationCategory, Boolean operatesBloodBank) {
        this(hospitalName, licenseNumber, phone, address, city, state, pincode, latitude, longitude,
                registrationCategory, operatesBloodBank, null, null, null, null);
    }
}
