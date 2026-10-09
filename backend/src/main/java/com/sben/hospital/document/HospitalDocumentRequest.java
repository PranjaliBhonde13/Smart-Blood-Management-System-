package com.sben.hospital.document;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An administrator's request for a corrected or additional document. While
 * OPEN or SUBMITTED it is mandatory for the hospital; it becomes FULFILLED when
 * an administrator approves the document uploaded against it.
 */
@Entity
@Table(name = "hospital_document_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HospitalDocumentRequest extends BaseEntity {

    public enum Status { OPEN, SUBMITTED, FULFILLED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private HospitalDocumentType documentType;

    /** Short name for an additional document, e.g. a fire safety certificate. */
    @Column(length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public boolean isOutstanding() {
        return status == Status.OPEN || status == Status.SUBMITTED;
    }

    public String displayName() {
        return title != null && !title.isBlank() ? title : documentType.label();
    }
}
