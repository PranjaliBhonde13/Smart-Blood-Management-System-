package com.sben.bloodbank;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Blood bank profile — 1:1 with a {@code users} row of role BLOOD_BANK. */
@Entity
@Table(name = "blood_banks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloodBank extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    /** Official contact phone. */
    @Column(length = 15)
    private String phone;

    @Column(name = "alternate_phone", length = 15)
    private String alternatePhone;

    @Column(length = 255)
    private String website;

    /** Stored filename of the logo in the private upload dir (never exposed). */
    @Column(name = "logo_file", length = 255)
    private String logoFile;

    @Column(length = 255)
    private String address;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    // ---------- license ----------

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    /** Optional: not every licensing authority prints an issue date. */
    @Column(name = "license_issue_date")
    private LocalDate licenseIssueDate;

    /** Optional: when present, the license stops being valid after this day. */
    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    /** Stored filename of the license certificate in the private upload dir (never exposed). */
    @Column(name = "license_document", length = 255)
    private String licenseDocument;

    /** Sanitised original filename, for display only. */
    @Column(name = "license_document_name", length = 255)
    private String licenseDocumentName;

    @Column(name = "license_document_type", length = 50)
    private String licenseDocumentType;

    @Column(name = "license_document_size")
    private Long licenseDocumentSize;

    /** When the current license details/document were (re)submitted for review. */
    @Column(name = "license_submitted_at")
    private LocalDateTime licenseSubmittedAt;

    // ---------- verification ----------

    /** Set by an admin after document checks (Module 6). */
    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** Admin user id who approved the license. */
    @Column(name = "verified_by")
    private Long verifiedBy;

    /** Why an admin rejected verification; cleared when the owner resubmits. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /** True while waiting for an admin decision (not verified, not rejected). */
    public boolean isPendingVerification() {
        return !verified && rejectedAt == null;
    }

    /** The license has a known expiry date that is already in the past. */
    public boolean isLicenseExpired() {
        return licenseExpiryDate != null && licenseExpiryDate.isBefore(LocalDate.now());
    }

    /**
     * May this bank act on the network (fulfil requests, appear in searches)?
     * Requires admin verification AND a license that has not expired.
     */
    public boolean isActiveOnNetwork() {
        return verified && !isLicenseExpired();
    }

    /** Derived status; expiry is evaluated live so it never depends on a batch job. */
    public LicenseStatus getVerificationStatus() {
        if (isLicenseExpired()) return LicenseStatus.EXPIRED;
        if (verified) return LicenseStatus.VERIFIED;
        if (rejectedAt != null) return LicenseStatus.REJECTED;
        if (licenseDocument == null) return LicenseStatus.NOT_SUBMITTED;
        return LicenseStatus.PENDING;
    }

    /** Clear any previous decision and put the license back in the admin queue. */
    public void resubmitForVerification() {
        verified = false;
        verifiedAt = null;
        verifiedBy = null;
        rejectedAt = null;
        rejectionReason = null;
        licenseSubmittedAt = LocalDateTime.now();
    }
}
