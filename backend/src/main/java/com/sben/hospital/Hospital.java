package com.sben.hospital;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Hospital profile — 1:1 with a {@code users} row of role HOSPITAL. */
@Entity
@Table(name = "hospitals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Hospital extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "hospital_name", nullable = false, length = 100)
    private String hospitalName;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    /** Decides which configured document requirements apply (see HospitalDocumentRequirement). */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_category", length = 40)
    private HospitalCategory registrationCategory;

    /** Runs its own blood bank, so it must also hold a blood bank license. */
    @Column(name = "operates_blood_bank", nullable = false)
    private boolean operatesBloodBank;

    @Column(length = 15)
    private String phone;

    /** 24x7 emergency / blood desk number. */
    @Column(name = "emergency_phone", length = 15)
    private String emergencyPhone;

    @Column(name = "official_email", length = 150)
    private String officialEmail;

    /** Person responsible for blood requests (e.g. blood transfusion officer). */
    @Column(name = "contact_person_name", length = 100)
    private String contactPersonName;

    @Column(name = "contact_person_designation", length = 100)
    private String contactPersonDesignation;

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

    /**
     * Set by an admin after document checks (Module 6). On its own this does not
     * let the hospital act: HospitalVerificationService also requires every
     * applicable mandatory document to be approved and unexpired.
     */
    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "verified_at")
    private java.time.LocalDateTime verifiedAt;

    /** Admin user id who approved the hospital. */
    @Column(name = "verified_by")
    private Long verifiedBy;

    /** Why an admin rejected verification; cleared when the owner resubmits. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "rejected_at")
    private java.time.LocalDateTime rejectedAt;

    /** True while waiting for an admin decision (not verified, not rejected). */
    public boolean isPendingVerification() {
        return !verified && rejectedAt == null;
    }
}
