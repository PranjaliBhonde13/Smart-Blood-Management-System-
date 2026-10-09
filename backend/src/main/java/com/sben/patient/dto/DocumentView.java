package com.sben.patient.dto;

import com.sben.patient.document.DocumentCategory;
import com.sben.patient.document.PatientDocument;
import com.sben.patient.document.PatientDocumentStatus;
import com.sben.patient.document.PatientDocumentType;

import java.time.LocalDateTime;

/** Document metadata safe to show the owner/admins. The stored filename is never included. */
public record DocumentView(
        Long id, DocumentCategory category, PatientDocumentType documentType, String documentTypeLabel,
        String fileName, String contentType, Long fileSize, LocalDateTime uploadedAt,
        PatientDocumentStatus status, String reviewRemarks, LocalDateTime reviewedAt,
        String confirmedBloodGroup, boolean current
) {
    public static DocumentView from(PatientDocument d) {
        return new DocumentView(d.getId(), d.getCategory(), d.getDocumentType(), d.getDocumentType().label(),
                d.getOriginalFilename(), d.getContentType(), d.getFileSize(), d.getUploadedAt(),
                d.getStatus(), d.getReviewRemarks(), d.getReviewedAt(), d.getConfirmedBloodGroup(), d.isCurrent());
    }
}
