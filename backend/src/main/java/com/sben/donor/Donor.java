package com.sben.donor;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    private Integer age;

    @Column(length = 50)
    private String city;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Availability availability = Availability.AVAILABLE;
}
