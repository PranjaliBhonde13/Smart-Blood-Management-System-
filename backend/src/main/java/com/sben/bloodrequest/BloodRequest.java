package com.sben.bloodrequest;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A patient's request for blood. Verified by a hospital, then progressed through
 * the {@link RequestStatus} lifecycle until fulfilled and closed.
 */
@Entity
@Table(name = "blood_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloodRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → patients.id (the raiser). */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** FK → hospitals.id; assigned when a hospital picks up / verifies. */
    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    @Column(name = "units_required", nullable = false)
    private Integer unitsRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Urgency urgency = Urgency.MEDIUM;

    @Column(name = "medical_report", length = 255)
    private String medicalReport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "request_date", nullable = false)
    private LocalDateTime requestDate;

    // ---------- details (V10); NULL on requests created before them ----------

    @Enumerated(EnumType.STRING)
    @Column(name = "request_for", length = 10)
    private RequestFor requestFor;

    /** Snapshot of the recipient's name/age at creation (the requester's own for SELF). */
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_age")
    private Integer recipientAge;

    /** Requester's relationship to the recipient (OTHER only), e.g. "Father". */
    @Column(name = "recipient_relationship", length = 50)
    private String recipientRelationship;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private BloodComponent component;

    @Column(name = "required_by")
    private LocalDateTime requiredBy;

    @Column(name = "preferred_hospital_id")
    private Long preferredHospitalId;

    @Column(name = "preferred_hospital_name", length = 100)
    private String preferredHospitalName;

    @Column(name = "location_notes", length = 255)
    private String locationNotes;

    /** Set by the server at creation; never accepted from the client. */
    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group_source", length = 20)
    private BloodGroupSource bloodGroupSource;

    /** Hospital/admin user who confirmed (or corrected) the requested group. */
    @Column(name = "blood_group_confirmed_by")
    private Long bloodGroupConfirmedBy;

    @Column(name = "blood_group_confirmed_at")
    private LocalDateTime bloodGroupConfirmedAt;

    /**
     * May this request be approved as far as its blood group is concerned?
     * Legacy rows (no source) keep the old flow; otherwise the group must come
     * from a verified profile or have been confirmed by a hospital/admin.
     */
    public boolean isBloodGroupApprovable() {
        return bloodGroupSource == null
                || bloodGroupSource == BloodGroupSource.PROFILE_VERIFIED
                || bloodGroupConfirmedAt != null;
    }

    @PrePersist
    void onCreate() {
        if (requestDate == null) requestDate = LocalDateTime.now();
    }
}
