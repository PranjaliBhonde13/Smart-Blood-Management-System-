package com.sben.hospital.document;

import com.sben.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One uploaded hospital document. Rows are never deleted or overwritten: a
 * replacement creates a new row and marks this one superseded, and a removal
 * only clears {@code current}. That keeps the full submission history for audits.
 */
@Entity
@Table(name = "hospital_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HospitalDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private HospitalDocumentType documentType;

    /** The administrator request this upload answers, if any. */
    @Column(name = "document_request_id")
    private Long documentRequestId;

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

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    /** False once replaced or removed; such rows remain only as history. */
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

    /** Rejection reason or approval remarks from the reviewing administrator. */
    @Column(name = "review_remarks", length = 1000)
    private String reviewRemarks;

    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    @Column(name = "removed_by")
    private Long removedBy;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "expiry_warning_sent_at")
    private LocalDateTime expiryWarningSentAt;

    /** Has a known expiry date that is already in the past. */
    public boolean isExpired(LocalDate today) {
        return expiryDate != null && expiryDate.isBefore(today);
    }

    /** Stored status, except that a pending or approved document past its expiry date reads as EXPIRED. */
    public DocumentStatus effectiveStatus(LocalDate today) {
        if ((status == DocumentStatus.PENDING || status == DocumentStatus.VERIFIED) && isExpired(today)) {
            return DocumentStatus.EXPIRED;
        }
        return status;
    }

    /** Approved and not expired: the only state that satisfies a requirement. */
    public boolean isValid(LocalDate today) {
        return current && effectiveStatus(today) == DocumentStatus.VERIFIED;
    }
}
