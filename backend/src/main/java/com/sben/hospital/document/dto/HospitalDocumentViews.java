package com.sben.hospital.document.dto;

import com.sben.hospital.HospitalCategory;
import com.sben.hospital.document.*;
import com.sben.hospital.dto.HospitalResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Request/response shapes for hospital documents. Stored filenames never leave
 * the server: clients fetch files through the authorised download endpoints.
 * Nothing here lets a client set a verification status.
 */
public final class HospitalDocumentViews {

    private HospitalDocumentViews() {}

    /** One uploaded document (current or historical). */
    public record DocumentView(
            Long id, HospitalDocumentType documentType, String documentTypeLabel, Long requestId,
            String fileName, String contentType, long fileSize, String sha256,
            LocalDate issueDate, LocalDate expiryDate, Long daysToExpiry,
            DocumentStatus status, boolean current,
            LocalDateTime uploadedAt, Long uploadedBy,
            LocalDateTime reviewedAt, Long reviewedBy, String reviewRemarks,
            LocalDateTime supersededAt, LocalDateTime removedAt
    ) {
        public static DocumentView from(HospitalDocument d, LocalDate today) {
            return new DocumentView(d.getId(), d.getDocumentType(), d.getDocumentType().label(),
                    d.getDocumentRequestId(), d.getOriginalFilename(), d.getContentType(),
                    d.getFileSize() == null ? 0 : d.getFileSize(), d.getSha256(),
                    d.getIssueDate(), d.getExpiryDate(),
                    d.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, d.getExpiryDate()),
                    d.effectiveStatus(today), d.isCurrent(),
                    d.getUploadedAt(), d.getUploadedBy(),
                    d.getReviewedAt(), d.getReviewedBy(), d.getReviewRemarks(),
                    d.getSupersededAt(), d.getRemovedAt());
        }
    }

    /** One applicable requirement, or one outstanding administrator request, and how the hospital stands on it. */
    public record RequirementView(
            Long requirementId, Long requestId, HospitalDocumentType documentType, String label,
            String description, boolean mandatory, boolean expiryDateRequired, boolean multiple,
            RequirementState state, String explanation, List<Long> documentIds
    ) {}

    /** The hospital's verification picture, computed on the server from stored data only. */
    public record Compliance(
            HospitalVerificationStatus status,
            String message,
            boolean fullyVerified,
            boolean adminVerified,
            boolean documentsComplete,
            boolean profileComplete,
            List<String> missingProfileFields,
            boolean inLegacyGrace,
            LocalDate legacyGraceUntil,
            int pendingReviewCount,
            List<String> outstanding,
            List<RequirementView> requirements
    ) {}

    public record DocumentRequestView(
            Long id, HospitalDocumentType documentType, String label, String title, String reason,
            HospitalDocumentRequest.Status status, LocalDateTime requestedAt, Long requestedBy,
            LocalDateTime resolvedAt
    ) {
        public static DocumentRequestView from(HospitalDocumentRequest r) {
            return new DocumentRequestView(r.getId(), r.getDocumentType(), r.displayName(), r.getTitle(),
                    r.getReason(), r.getStatus(), r.getRequestedAt(), r.getRequestedBy(), r.getResolvedAt());
        }
    }

    /** A document type the hospital may upload, with the rule that applies to it. */
    public record UploadableType(HospitalDocumentType type, String label, boolean multiple,
                                 boolean mandatory, boolean expiryDateRequired) {}

    public record UploadRules(long maxFileSizeBytes, List<String> allowedExtensions,
                              List<UploadableType> types) {}

    /** Everything the hospital's Documents & Verification section needs. */
    public record HospitalDocumentsOverview(
            Compliance compliance,
            List<DocumentView> documents,
            List<DocumentRequestView> requests,
            List<DocumentView> history,
            UploadRules rules
    ) {}

    /** Everything an administrator needs to review one hospital. */
    public record HospitalReview(
            HospitalResponse hospital,
            Compliance compliance,
            List<DocumentView> documents,
            List<DocumentView> history,
            List<DocumentRequestView> requests,
            long activeRequests
    ) {}

    // ---------- admin inputs ----------

    public record ApproveDocumentRequest(@Size(max = 1000) String remarks) {}

    public record RejectDocumentRequest(
            @NotBlank(message = "Rejection remarks are required") @Size(max = 1000) String remarks,
            /** Also open a request for a corrected document (makes the resubmission explicit). */
            Boolean requestCorrection
    ) {}

    public record CreateDocumentRequest(
            @NotNull(message = "Document type is required") HospitalDocumentType documentType,
            @Size(max = 150) String title,
            @NotBlank(message = "Explain what is needed") @Size(max = 1000) String reason
    ) {}

    // ---------- requirement configuration ----------

    public record RequirementConfig(
            Long id, HospitalDocumentType documentType, String documentTypeLabel,
            HospitalCategory registrationCategory, RequirementCondition appliesWhen,
            boolean mandatory, boolean expiryDateRequired, String description, boolean active, int sortOrder
    ) {
        public static RequirementConfig from(HospitalDocumentRequirement r) {
            return new RequirementConfig(r.getId(), r.getDocumentType(), r.getDocumentType().label(),
                    r.getRegistrationCategory(), r.getAppliesWhen(), r.isMandatory(), r.isExpiryDateRequired(),
                    r.getDescription(), r.isActive(), r.getSortOrder());
        }
    }

    public record UpdateRequirementRequest(Boolean mandatory, Boolean expiryDateRequired, Boolean active,
                                           @Size(max = 500) String description) {}

    public record CreateRequirementRequest(
            @NotNull HospitalDocumentType documentType,
            HospitalCategory registrationCategory,
            RequirementCondition appliesWhen,
            boolean mandatory,
            boolean expiryDateRequired,
            @Size(max = 500) String description,
            Integer sortOrder
    ) {}
}
