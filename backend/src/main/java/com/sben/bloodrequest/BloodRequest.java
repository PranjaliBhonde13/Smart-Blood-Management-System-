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

    @PrePersist
    void onCreate() {
        if (requestDate == null) requestDate = LocalDateTime.now();
    }
}
