package com.sben.donor;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/** Donor profile — 1:1 with a {@code users} row of role DONOR. */
@Entity
@Table(name = "donors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Donor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    /** Source of the donor's age. */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    /** Legacy age for profiles saved before date of birth existed; see {@link #currentAge()}. */
    private Integer age;

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

    @Column(name = "has_donated_before")
    private Boolean hasDonatedBefore;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Availability availability = Availability.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", length = 20)
    private ContactMethod preferredContactMethod;

    /** Consent to receive donation requests. NULL = not answered yet, FALSE = opted out. */
    @Column(name = "contact_consent")
    private Boolean contactConsent;

    @Column(name = "contact_consent_updated_at")
    private LocalDateTime contactConsentUpdatedAt;

    @Column(name = "alternate_phone", length = 15)
    private String alternatePhone;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    /** Stored filename of the optional profile photo (private upload dir). */
    @Column(name = "photo_file", length = 255)
    private String photoFile;

    @Embedded
    private HealthScreening healthScreening;

    /** Age in whole years from the date of birth, else the legacy stored age. */
    public Integer currentAge() {
        return dateOfBirth != null ? Period.between(dateOfBirth, LocalDate.now()).getYears() : age;
    }
}
