package com.sben.hospital.document;

import com.sben.audit.AuditService;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.dto.HospitalDocumentViews.*;
import com.sben.hospital.dto.HospitalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Administrator side of hospital documents: review a hospital's submission,
 * approve or reject each document, request corrected or additional documents
 * and maintain the requirement rules. Callers must already be authorised as
 * ADMIN (the controller is ADMIN-only); the acting admin's id comes from the
 * authenticated principal, never from the request body.
 */
@Service
@RequiredArgsConstructor
public class HospitalDocumentReviewService {

    private final HospitalRepository hospitalRepository;
    private final HospitalDocumentRepository documentRepository;
    private final HospitalDocumentRequestRepository requestRepository;
    private final HospitalDocumentRequirementRepository requirementRepository;
    private final HospitalVerificationService verificationService;
    private final HospitalDocumentNotifier notifier;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public HospitalReview review(Long hospitalId) {
        Hospital hospital = requireHospital(hospitalId);
        LocalDate today = LocalDate.now();
        Compliance compliance = verificationService.assess(hospital);
        List<HospitalDocument> history = documentRepository.findByHospitalIdAndDeletedFalseOrderByUploadedAtDesc(hospitalId);
        return new HospitalReview(
                HospitalResponse.from(hospital, compliance),
                compliance,
                history.stream().filter(HospitalDocument::isCurrent).map(d -> DocumentView.from(d, today)).toList(),
                history.stream().map(d -> DocumentView.from(d, today)).toList(),
                requestRepository.findByHospitalIdAndDeletedFalseOrderByRequestedAtDesc(hospitalId).stream()
                        .map(DocumentRequestView::from).toList(),
                notifier.activeRequests(hospital));
    }

    /** Approve one current document. Refused when it is expired or lacks a required expiry date. */
    @Transactional
    public HospitalReview approve(Long documentId, Long adminId, String remarks) {
        HospitalDocument doc = requireCurrent(documentId);
        Hospital hospital = requireHospital(doc.getHospitalId());
        LocalDate today = LocalDate.now();
        DocumentStatus status = doc.effectiveStatus(today);
        if (status == DocumentStatus.EXPIRED) {
            throw new BusinessException("Cannot approve: this document expired on " + doc.getExpiryDate(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (status == DocumentStatus.VERIFIED) {
            throw new BusinessException("This document is already approved", HttpStatus.CONFLICT);
        }
        boolean expiryRequired = verificationService.requirementFor(hospital, doc.getDocumentType())
                .map(HospitalDocumentRequirement::isExpiryDateRequired).orElse(false);
        if (expiryRequired && doc.getExpiryDate() == null) {
            throw new BusinessException("Cannot approve: this document type needs an expiry date. Reject it and ask for a re-upload with dates.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        boolean wasFullyVerified = verificationService.isFullyVerified(hospital);
        doc.setStatus(DocumentStatus.VERIFIED);
        doc.setReviewedBy(adminId);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setReviewRemarks(trimToNull(remarks, 1000));
        documentRepository.save(doc);
        String label = label(doc);
        if (doc.getDocumentRequestId() != null) {
            requestRepository.findById(doc.getDocumentRequestId()).filter(HospitalDocumentRequest::isOutstanding)
                    .ifPresent(r -> resolve(r, HospitalDocumentRequest.Status.FULFILLED, adminId));
        }
        auditService.record("HOSPITAL_DOCUMENT_APPROVED", "HospitalDocument", doc.getId(),
                hospital.getHospitalName() + " — " + label + (doc.getReviewRemarks() != null ? " — " + doc.getReviewRemarks() : ""));

        Compliance after = verificationService.assess(hospital);
        String extra;
        if (!wasFullyVerified && after.fullyVerified()) {
            extra = " Your hospital is fully verified and active on the network.";
        } else if (after.documentsComplete() && !hospital.isVerified()) {
            extra = " All mandatory documents are approved; final approval of your hospital is pending.";
        } else {
            extra = "";
        }
        notifier.hospital(hospital, "Document approved", label + " was approved by an administrator." + extra);
        return review(hospital.getId());
    }

    /**
     * Reject one current document with mandatory remarks (also usable to revoke
     * an earlier approval). Optionally opens a request for a corrected document.
     */
    @Transactional
    public HospitalReview reject(Long documentId, Long adminId, String remarks, boolean requestCorrection) {
        String reason = trimToNull(remarks, 1000);
        if (reason == null) {
            throw new BusinessException("Rejection remarks are required", HttpStatus.BAD_REQUEST);
        }
        HospitalDocument doc = requireCurrent(documentId);
        Hospital hospital = requireHospital(doc.getHospitalId());
        if (doc.getStatus() == DocumentStatus.REJECTED) {
            throw new BusinessException("This document is already rejected", HttpStatus.CONFLICT);
        }
        boolean wasFullyVerified = verificationService.isFullyVerified(hospital);
        boolean wasApproved = doc.getStatus() == DocumentStatus.VERIFIED;
        doc.setStatus(DocumentStatus.REJECTED);
        doc.setReviewedBy(adminId);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setReviewRemarks(reason);
        documentRepository.save(doc);

        String label = label(doc);
        if (doc.getDocumentRequestId() != null) {
            // The request stays open until an acceptable document is approved.
            requestRepository.findById(doc.getDocumentRequestId())
                    .filter(r -> r.getStatus() == HospitalDocumentRequest.Status.SUBMITTED)
                    .ifPresent(r -> { r.setStatus(HospitalDocumentRequest.Status.OPEN); requestRepository.save(r); });
        } else if (requestCorrection) {
            HospitalDocumentRequest r = requestRepository.save(HospitalDocumentRequest.builder()
                    .hospitalId(hospital.getId()).documentType(doc.getDocumentType())
                    .reason("Corrected document needed: " + reason)
                    .status(HospitalDocumentRequest.Status.OPEN)
                    .requestedBy(adminId).requestedAt(LocalDateTime.now()).build());
            auditService.record("HOSPITAL_DOCUMENT_REQUESTED", "HospitalDocumentRequest", r.getId(),
                    hospital.getHospitalName() + " — corrected " + doc.getDocumentType().label());
        }
        auditService.record(wasApproved ? "HOSPITAL_DOCUMENT_APPROVAL_REVOKED" : "HOSPITAL_DOCUMENT_REJECTED",
                "HospitalDocument", doc.getId(), hospital.getHospitalName() + " — " + label + " — " + reason);

        Compliance after = verificationService.assess(hospital);
        boolean lost = wasFullyVerified && !after.fullyVerified();
        notifier.hospital(hospital, "Document rejected",
                label + " was not accepted: " + reason + ". Upload a corrected document from your profile."
                        + (lost ? " Restricted request actions are disabled until it is approved." : ""));
        if (lost) {
            long active = notifier.activeRequests(hospital);
            notifier.admins("Hospital no longer fully verified",
                    hospital.getHospitalName() + " lost its verified status (" + label + " rejected). "
                            + (active > 0 ? active + " in-progress request(s) owned by this hospital are paused. " : ""));
        }
        return review(hospital.getId());
    }

    /** Ask a hospital for a corrected or additional document. It is mandatory until fulfilled or cancelled. */
    @Transactional
    public HospitalReview requestDocument(Long hospitalId, Long adminId, CreateDocumentRequest body) {
        Hospital hospital = requireHospital(hospitalId);
        String title = trimToNull(body.title(), 150);
        if (body.documentType() == HospitalDocumentType.ADDITIONAL_REQUESTED && title == null) {
            throw new BusinessException("Give the additional document a name", HttpStatus.BAD_REQUEST);
        }
        String reason = trimToNull(body.reason(), 1000);
        if (reason == null) throw new BusinessException("Explain what is needed", HttpStatus.BAD_REQUEST);
        boolean wasFullyVerified = verificationService.isFullyVerified(hospital);
        HospitalDocumentRequest r = requestRepository.save(HospitalDocumentRequest.builder()
                .hospitalId(hospitalId).documentType(body.documentType()).title(title).reason(reason)
                .status(HospitalDocumentRequest.Status.OPEN)
                .requestedBy(adminId).requestedAt(LocalDateTime.now()).build());
        auditService.record("HOSPITAL_DOCUMENT_REQUESTED", "HospitalDocumentRequest", r.getId(),
                hospital.getHospitalName() + " — " + r.displayName() + " — " + reason);
        notifier.hospital(hospital, "Document requested",
                "An administrator asked for: " + r.displayName() + ". " + reason
                        + " Upload it under Documents & Verification on your profile."
                        + (wasFullyVerified ? " Restricted request actions are paused until it is approved." : ""));
        return review(hospitalId);
    }

    @Transactional
    public HospitalReview cancelRequest(Long requestId, Long adminId) {
        HospitalDocumentRequest r = requestRepository.findById(requestId).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document request", requestId));
        if (!r.isOutstanding()) throw new BusinessException("This request is already closed", HttpStatus.CONFLICT);
        Hospital hospital = requireHospital(r.getHospitalId());
        resolve(r, HospitalDocumentRequest.Status.CANCELLED, adminId);
        auditService.record("HOSPITAL_DOCUMENT_REQUEST_CANCELLED", "HospitalDocumentRequest", r.getId(),
                hospital.getHospitalName() + " — " + r.displayName());
        notifier.hospital(hospital, "Document request withdrawn",
                "The administrator no longer needs: " + r.displayName() + ".");
        return review(hospital.getId());
    }

    // ---------- requirement rules ----------

    @Transactional(readOnly = true)
    public List<RequirementConfig> requirements() {
        return requirementRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .map(RequirementConfig::from).toList();
    }

    @Transactional
    public RequirementConfig updateRequirement(Long id, UpdateRequirementRequest body) {
        HospitalDocumentRequirement r = requirementRepository.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document requirement", id));
        if (body.mandatory() != null) r.setMandatory(body.mandatory());
        if (body.expiryDateRequired() != null) r.setExpiryDateRequired(body.expiryDateRequired());
        if (body.active() != null) r.setActive(body.active());
        if (body.description() != null) r.setDescription(trimToNull(body.description(), 500));
        requirementRepository.save(r);
        auditService.record("HOSPITAL_DOC_REQUIREMENT_UPDATED", "HospitalDocumentRequirement", id, describe(r));
        return RequirementConfig.from(r);
    }

    @Transactional
    public RequirementConfig createRequirement(CreateRequirementRequest body) {
        if (body.documentType() == HospitalDocumentType.ADDITIONAL_REQUESTED) {
            throw new BusinessException("Additional documents are requested per hospital, not as a rule", HttpStatus.BAD_REQUEST);
        }
        RequirementCondition when = body.appliesWhen() == null ? RequirementCondition.ALWAYS : body.appliesWhen();
        boolean duplicate = requirementRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .anyMatch(r -> r.getDocumentType() == body.documentType()
                        && r.getRegistrationCategory() == body.registrationCategory()
                        && r.getAppliesWhen() == when);
        if (duplicate) throw new BusinessException("A rule for this document, category and condition already exists", HttpStatus.CONFLICT);
        HospitalDocumentRequirement r = requirementRepository.save(HospitalDocumentRequirement.builder()
                .documentType(body.documentType()).registrationCategory(body.registrationCategory())
                .appliesWhen(when).mandatory(body.mandatory()).expiryDateRequired(body.expiryDateRequired())
                .description(trimToNull(body.description(), 500)).active(true)
                .sortOrder(body.sortOrder() == null ? 100 : body.sortOrder()).build());
        auditService.record("HOSPITAL_DOC_REQUIREMENT_CREATED", "HospitalDocumentRequirement", r.getId(), describe(r));
        return RequirementConfig.from(r);
    }

    // ---------- helpers ----------

    private void resolve(HospitalDocumentRequest r, HospitalDocumentRequest.Status status, Long adminId) {
        r.setStatus(status);
        r.setResolvedBy(adminId);
        r.setResolvedAt(LocalDateTime.now());
        requestRepository.save(r);
    }

    private String label(HospitalDocument doc) {
        if (doc.getDocumentRequestId() != null) {
            return requestRepository.findById(doc.getDocumentRequestId())
                    .map(HospitalDocumentRequest::displayName).orElse(doc.getDocumentType().label());
        }
        return doc.getDocumentType().label();
    }

    private HospitalDocument requireCurrent(Long documentId) {
        HospitalDocument doc = documentRepository.findById(documentId).filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        if (!doc.isCurrent()) {
            throw new BusinessException("This document was replaced or removed — review the current version",
                    HttpStatus.CONFLICT);
        }
        return doc;
    }

    private Hospital requireHospital(Long hospitalId) {
        return hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital", hospitalId));
    }

    private static String describe(HospitalDocumentRequirement r) {
        return r.getDocumentType() + " category=" + (r.getRegistrationCategory() == null ? "ALL" : r.getRegistrationCategory())
                + " when=" + r.getAppliesWhen() + " mandatory=" + r.isMandatory()
                + " expiryRequired=" + r.isExpiryDateRequired() + " active=" + r.isActive();
    }

    private static String trimToNull(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
