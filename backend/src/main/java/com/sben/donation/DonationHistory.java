package com.sben.donation;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Record of a fulfilment for a blood request — either by a donor or by a blood
 * bank (hence both FK columns are nullable). Drives donation history + reports.
 */
@Entity
@Table(name = "donation_history")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DonationHistory {

    public enum Status { SCHEDULED, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** Set when a donor fulfils the request. */
    @Column(name = "donor_id")
    private Long donorId;

    /** Set when a blood bank fulfils the request from stock. */
    @Column(name = "blood_bank_id")
    private Long bloodBankId;

    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "units_donated", nullable = false)
    private Integer unitsDonated = 1;

    @Column(name = "donation_date")
    private LocalDate donationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.SCHEDULED;

    @CreatedDate  @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate @Column(name = "updated_at") private LocalDateTime updatedAt;
    @CreatedBy  @Column(name = "created_by", updatable = false) private Long createdBy;
    @LastModifiedBy @Column(name = "updated_by") private Long updatedBy;
}
