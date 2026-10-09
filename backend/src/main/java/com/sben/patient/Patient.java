package com.sben.patient;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Patient profile — 1:1 with a {@code users} row of role PATIENT. The account's
 * name, email and phone live on {@code users}; verification state lives on
 * {@code patient_documents} and is never stored here.
 */
@Entity
@Table(name = "patients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** Self-declared group; trusted only while a VERIFIED blood-group document confirms it. */
    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Legacy value from before date of birth was collected; cleared once a DOB is saved. */
    private Integer age;

    @Column(length = 20)
    private String gender;

    @Column(length = 255)
    private String address;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 6)
    private String pincode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    /** Emergency contact phone (column name predates the contact name). */
    @Column(name = "emergency_contact", length = 15)
    private String emergencyContact;

    /** NULL = not answered; FALSE = no request-update emails (in-app notices still shown). */
    @Column(name = "notification_consent")
    private Boolean notificationConsent;

    @Column(name = "consent_updated_at")
    private LocalDateTime consentUpdatedAt;

    @Column(name = "privacy_acknowledged_at")
    private LocalDateTime privacyAcknowledgedAt;

    /** Age in whole years from the date of birth, falling back to the legacy value. */
    public Integer currentAge() {
        if (dateOfBirth != null) return Period.between(dateOfBirth, LocalDate.now()).getYears();
        return age;
    }
}
