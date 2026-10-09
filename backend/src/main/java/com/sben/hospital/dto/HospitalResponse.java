package com.sben.hospital.dto;

import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalCategory;
import com.sben.hospital.document.HospitalVerificationStatus;
import com.sben.hospital.document.dto.HospitalDocumentViews.Compliance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hospital profile as returned to its owner and to admins. {@code verified} is
 * the administrator's approval of the hospital; {@code fullyVerified} also
 * requires every applicable mandatory document to be approved and valid, and is
 * what the backend enforces. The document fields are null when the response was
 * built without a compliance assessment.
 */
public record HospitalResponse(
        Long id, Long userId, String hospitalName, String licenseNumber, String phone,
        String address, String city, String state, String pincode,
        BigDecimal latitude, BigDecimal longitude, boolean verified,
        String rejectionReason, LocalDateTime rejectedAt, LocalDateTime createdAt,
        HospitalCategory registrationCategory, boolean operatesBloodBank, LocalDateTime verifiedAt,
        Boolean fullyVerified, HospitalVerificationStatus verificationStatus, String verificationMessage,
        Boolean documentsComplete, Integer pendingDocuments,
        String emergencyPhone, String officialEmail, String contactPersonName, String contactPersonDesignation
) {
    public static HospitalResponse from(Hospital h) {
        return from(h, null);
    }

    public static HospitalResponse from(Hospital h, Compliance c) {
        return new HospitalResponse(h.getId(), h.getUserId(), h.getHospitalName(),
                h.getLicenseNumber(), h.getPhone(), h.getAddress(), h.getCity(), h.getState(),
                h.getPincode(), h.getLatitude(), h.getLongitude(), h.isVerified(),
                h.getRejectionReason(), h.getRejectedAt(), h.getCreatedAt(),
                h.getRegistrationCategory(), h.isOperatesBloodBank(), h.getVerifiedAt(),
                c == null ? null : c.fullyVerified(),
                c == null ? null : c.status(),
                c == null ? null : c.message(),
                c == null ? null : c.documentsComplete(),
                c == null ? null : c.pendingReviewCount(),
                h.getEmergencyPhone(), h.getOfficialEmail(), h.getContactPersonName(), h.getContactPersonDesignation());
    }
}
