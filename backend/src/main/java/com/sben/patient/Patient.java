package com.sben.patient;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/** Patient profile — 1:1 with a {@code users} row of role PATIENT. */
@Entity
@Table(name = "patients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    private Integer age;

    @Column(length = 10)
    private String gender;

    @Column(length = 255)
    private String address;

    @Column(name = "emergency_contact", length = 15)
    private String emergencyContact;
}
