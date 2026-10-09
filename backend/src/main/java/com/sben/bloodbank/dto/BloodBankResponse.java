package com.sben.bloodbank.dto;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.LicenseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Blood bank profile as returned to its owner and to admins. Stored filenames are
 * never exposed — clients only learn whether a document/logo exists and fetch
 * it through an authorised endpoint.
 */
public record BloodBankResponse(
        Long id, Long userId, String bankName, String contactEmail, String phone, String alternatePhone,
        String website, boolean hasLogo,
        String address, String city, String state, String pincode, BigDecimal latitude, BigDecimal longitude,
        String licenseNumber, LocalDate licenseIssueDate, LocalDate licenseExpiryDate,
        boolean hasLicenseDocument, String licenseDocumentName, String licenseDocumentType,
        Long licenseDocumentSize, LocalDateTime licenseSubmittedAt,
        LicenseStatus verificationStatus, boolean verified, LocalDateTime verifiedAt,
        String rejectionReason, LocalDateTime rejectedAt, LocalDateTime createdAt
) {
    public static BloodBankResponse from(BloodBank b) {
        return new BloodBankResponse(b.getId(), b.getUserId(), b.getBankName(), b.getContactEmail(),
                b.getPhone(), b.getAlternatePhone(), b.getWebsite(), b.getLogoFile() != null,
                b.getAddress(), b.getCity(), b.getState(), b.getPincode(), b.getLatitude(), b.getLongitude(),
                b.getLicenseNumber(), b.getLicenseIssueDate(), b.getLicenseExpiryDate(),
                b.getLicenseDocument() != null, b.getLicenseDocumentName(), b.getLicenseDocumentType(),
                b.getLicenseDocumentSize(), b.getLicenseSubmittedAt(),
                b.getVerificationStatus(), b.isVerified(), b.getVerifiedAt(),
                b.getRejectionReason(), b.getRejectedAt(), b.getCreatedAt());
    }
}
