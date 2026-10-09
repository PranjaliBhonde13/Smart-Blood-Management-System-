package com.sben.hospital.document;

import com.sben.common.entity.BaseEntity;
import com.sben.hospital.HospitalCategory;
import jakarta.persistence.*;
import lombok.*;

/**
 * A configured rule: hospitals in {@code registrationCategory} (all categories
 * when null) that meet {@code appliesWhen} need a document of {@code documentType}.
 * A category-specific rule overrides the all-categories rule for the same type.
 */
@Entity
@Table(name = "hospital_document_requirements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HospitalDocumentRequirement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private HospitalDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_category", length = 40)
    private HospitalCategory registrationCategory;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_when", nullable = false, length = 30)
    private RequirementCondition appliesWhen = RequirementCondition.ALWAYS;

    @Builder.Default
    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "expiry_date_required", nullable = false)
    private boolean expiryDateRequired;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
