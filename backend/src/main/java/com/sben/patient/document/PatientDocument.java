package com.sben.patient.document;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One uploaded patient verification document. Rows are never deleted: a
 * replacement creates a new row and marks this one non-current, so the full
 * submission history remains for audits.
 */
@Entity
@Table(name = "patient_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatientDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private PatientDocumentType documentType;

    /** Sanitised original filename, for display only. */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Random filename in the private upload directory (never exposed to clients). */
    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** SHA-256 of the stored bytes, so a file can be proven unchanged during an audit. */
    @Column(nullable = false, length = 64)
    private String sha256;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientDocumentStatus status = PatientDocumentStatus.PENDING;

    @Builder.Default
    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_remarks", length = 1000)
    private String reviewRemarks;

    /** Blood group the reviewer read on a BLOOD_GROUP document; null for identity documents. */
    @Column(name = "confirmed_blood_group", length = 5)
    private String confirmedBloodGroup;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    /** Send back for re-review (profile details it was checked against changed). */
    public void reopenForReview() {
        status = PatientDocumentStatus.PENDING;
        reviewedBy = null;
        reviewedAt = null;
        reviewRemarks = null;
        confirmedBloodGroup = null;
    }
}
