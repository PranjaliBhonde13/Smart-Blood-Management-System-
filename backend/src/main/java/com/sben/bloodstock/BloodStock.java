package com.sben.bloodstock;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One inventory line: units of a blood group held by a blood bank. Unique per
 * (bank, group) so each bank has exactly one row per blood type.
 */
@Entity
@Table(name = "blood_stock")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloodStock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_bank_id", nullable = false)
    private Long bloodBankId;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    @Column(name = "units_available", nullable = false)
    private Integer unitsAvailable = 0;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist @PreUpdate
    void touch() {
        lastUpdated = LocalDateTime.now();
    }
}
