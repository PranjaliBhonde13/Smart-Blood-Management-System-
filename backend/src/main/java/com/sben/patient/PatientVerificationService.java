package com.sben.patient;

import com.sben.audit.AuditService;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.document.*;
import com.sben.patient.dto.DocumentView;
import com.sben.patient.dto.PatientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Administrator review of patient documents. Callers must already be authorised
 * as ADMIN (controller-level @PreAuthorize). Identity and blood group are
 * reviewed independently; approving identity says nothing about blood group.
 */
@Service
@RequiredArgsConstructor
public class PatientVerificationService {

    private final PatientRepository patientRepository;
    private final PatientDocumentRepository documentRepository;
    private final PatientService patientService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** Patients with at least one current document awaiting review. */
    @Transactional(readOnly = true)
    public List<PatientResponse> queue() {
        List<Long> ids = documentRepository.findPatientIdsWithCurrentStatusIn(Set.of(PatientDocumentStatus.PENDING));
        return patientRepository.findAllById(ids).stream()
                .filter(p -> !p.isDeleted())
                .map(patientService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientReview detail(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        return new PatientReview(patientService.toResponse(patient),
                documentRepository.findByPatientIdAndDeletedFalseOrderByUploadedAtDesc(patientId)
                        .stream().map(DocumentView::from).toList());
    }

    public record PatientReview(PatientResponse profile, List<DocumentView> history) {}

    @Transactional(readOnly = true)
    public PatientService.StoredFile file(Long documentId) {
        return patientService.fileOf(requireDocument(documentId));
    }

    @Transactional
    public PatientReview review(Long documentId, ReviewDocumentRequest req, Long adminUserId) {
        PatientDocument doc = requireDocument(documentId);
        if (!doc.isCurrent()) {
            throw new BusinessException("This document was replaced; review the current one instead",
                    HttpStatus.CONFLICT);
        }
        Patient patient = patientRepository.findById(doc.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", doc.getPatientId()));
        String remarks = req.remarks() == null || req.remarks().isBlank() ? null : req.remarks().trim();

        switch (req.decision()) {
            case APPROVE -> {
                if (doc.getStatus() != PatientDocumentStatus.PENDING) {
                    throw new BusinessException("Only a pending document can be approved", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                if (doc.getCategory() == DocumentCategory.BLOOD_GROUP) {
                    String seen = req.confirmedBloodGroup();
                    if (seen == null || seen.isBlank()) {
                        throw new BusinessException("Enter the blood group shown on the document to approve it",
                                HttpStatus.BAD_REQUEST);
                    }
                    if (!seen.equals(patient.getBloodGroup())) {
                        throw new BusinessException("The document shows " + seen + " but the profile says "
                                + patient.getBloodGroup() + ". Reject it or request changes instead.",
                                HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    doc.setConfirmedBloodGroup(seen);
                }
                doc.setStatus(PatientDocumentStatus.VERIFIED);
            }
            case REJECT, REQUEST_CHANGES -> {
                if (remarks == null) {
                    throw new BusinessException("Remarks are required so the patient knows what to fix",
                            HttpStatus.BAD_REQUEST);
                }
                doc.setStatus(req.decision() == ReviewDocumentRequest.Decision.REJECT
                        ? PatientDocumentStatus.REJECTED : PatientDocumentStatus.ACTION_REQUIRED);
                doc.setConfirmedBloodGroup(null);
            }
        }
        doc.setReviewRemarks(remarks);
        doc.setReviewedBy(adminUserId);
        doc.setReviewedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        documentRepository.save(doc);

        String track = doc.getCategory() == DocumentCategory.IDENTITY ? "identity" : "blood group";
        auditService.record("PATIENT_" + (doc.getCategory() == DocumentCategory.IDENTITY ? "IDENTITY" : "BLOOD_GROUP")
                        + "_" + doc.getStatus().name(), "PatientDocument", doc.getId(),
                "patient " + patient.getId() + (doc.getConfirmedBloodGroup() != null ? " — group " + doc.getConfirmedBloodGroup() : "")
                        + (remarks != null ? " — " + remarks : ""));
        notificationService.notify(patient.getUserId(), switch (doc.getStatus()) {
                    case VERIFIED -> "Your " + track + " was verified";
                    case REJECTED -> "Your " + track + " document was rejected";
                    default -> "Action required on your " + track + " document";
                },
                doc.getStatus() == PatientDocumentStatus.VERIFIED
                        ? "An administrator verified your " + track + " document."
                        : "Administrator remarks: " + remarks + ". Upload a corrected document from your profile.",
                Notification.Type.VERIFICATION);
        return detail(patient.getId());
    }

    private PatientDocument requireDocument(Long id) {
        return documentRepository.findById(id)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }
}
