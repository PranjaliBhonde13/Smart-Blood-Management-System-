package com.sben.patient.dto;

import com.sben.patient.document.PatientVerificationStatus;
import com.sben.patient.document.TrackStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Patient profile as returned to its owner (and admins). Every verification
 * field is computed on the server from the document tracks.
 *
 * @param bloodGroupVerified true only when the current blood-group document is
 *                           VERIFIED and the reviewer confirmed exactly the
 *                           profile's blood group
 * @param phoneVerified      always false today: the platform has no SMS/OTP
 *                           verification for phone numbers yet
 * @param missingFields      required profile fields still empty (drives PROFILE_INCOMPLETE)
 */
public record PatientResponse(
        Long id, Long userId,
        String fullName, String email, boolean emailVerified, String phone, boolean phoneVerified,
        LocalDate dateOfBirth, Integer age, String bloodGroup, String gender,
        String address, String city, String state, String pincode, BigDecimal latitude, BigDecimal longitude,
        String emergencyContactName, String emergencyContactPhone,
        Boolean notificationConsent, LocalDateTime consentUpdatedAt, LocalDateTime privacyAcknowledgedAt,
        PatientVerificationStatus verificationStatus,
        Track identity,
        Track bloodGroupVerification,
        boolean bloodGroupVerified,
        String verifiedBloodGroup,
        List<String> missingFields
) {
    /** One verification track and its current document (null when not submitted). */
    public record Track(TrackStatus status, DocumentView document) {}
}
