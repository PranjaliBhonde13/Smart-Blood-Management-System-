package com.sben.donor.dto;

import com.sben.donor.Availability;
import com.sben.donor.ContactMethod;
import com.sben.donor.ScreeningAnswer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload to create/update the caller's own donor profile. A PUT replaces the
 * whole profile, so omitted optional fields are cleared. Age is never accepted:
 * it is derived from {@code dateOfBirth}. Cross-field rules (coordinates as a
 * pair, donation dates, screening completeness) are checked in DonorService.
 */
public record DonorRequest(
        // --- personal (fullName and phone are stored on the user account) ---
        @NotBlank @Size(max = 100) String fullName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "invalid blood group") String bloodGroup,
        @NotBlank @Pattern(regexp = PHONE, message = "enter a valid 10-digit Indian mobile number") String phone,
        @Pattern(regexp = "^(MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY)$", message = "invalid gender") String gender,

        // --- address & location ---
        @Size(max = 255) String address,
        @NotBlank @Size(max = 50) String city,
        @NotBlank @Size(max = 50) String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "PIN code must be 6 digits") String pincode,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,

        // --- donation history ---
        Boolean hasDonatedBefore,
        @PastOrPresent LocalDate lastDonationDate,

        // --- preferences ---
        @NotNull Availability availability,
        ContactMethod preferredContactMethod,
        @NotNull(message = "please choose whether you want to receive donation requests") Boolean contactConsent,

        // --- optional contacts ---
        @Pattern(regexp = PHONE, message = "enter a valid 10-digit Indian mobile number") String alternatePhone,
        @Size(max = 100) String emergencyContactName,
        @Pattern(regexp = PHONE, message = "enter a valid 10-digit Indian mobile number") String emergencyContactPhone,

        // --- self-declared screening; null clears saved answers ---
        @Valid HealthScreeningRequest healthScreening
) {
    /** 10-digit Indian mobile number, optionally prefixed with +91, 91 or 0; spaces and dashes allowed. */
    public static final String PHONE = "^(?:\\+?91|0)?[\\s-]*[6-9](?:[\\s-]*\\d){9}$";

    public record HealthScreeningRequest(
            ScreeningAnswer feelingWell,
            ScreeningAnswer recentIllness,
            ScreeningAnswer currentMedication,
            ScreeningAnswer recentProcedure,
            ScreeningAnswer chronicCondition,
            ScreeningAnswer pregnancy,
            Boolean declarationAccepted
    ) {}
}
