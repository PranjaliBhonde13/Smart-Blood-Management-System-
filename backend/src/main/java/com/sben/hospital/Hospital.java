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

    @Column(length = 15)
    private String phone;

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

    /** Set by an admin after document checks (Module 6). */
    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;
}
