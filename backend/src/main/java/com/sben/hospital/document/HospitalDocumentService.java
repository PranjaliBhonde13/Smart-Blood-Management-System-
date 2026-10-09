package com.sben.hospital.document;

import com.sben.audit.AuditService;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.dto.HospitalDocumentViews.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The hospital's own documents: upload, replace, remove and download. Every
 * upload starts PENDING and only an administrator can change that
 * ({@link HospitalDocumentReviewService}). Replacing a document — including an
 * approved one — puts the new file back into review; the old file is kept as history.
 */
@Service
@RequiredArgsConstructor
public class HospitalDocumentService {

    private final HospitalRepository hospitalRepository;
    private final HospitalDocumentRepository documentRepository;
    private final HospitalDocumentRequestRepository requestRepository;
    private final HospitalVerificationService verificationService;
    private final HospitalDocumentNotifier notifier;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final HospitalDocumentProperties properties;

    @Transactional(readOnly = true)
    public HospitalDocumentsOverview overview(Long userId) {
        return overview(requireMine(userId));
    }

    /**
     * Upload a document. {@code replacesDocumentId} replaces a specific current
     * document; {@code requestId} answers an administrator request. A single-slot
     * type (e.g. registration certificate) always replaces the current one.
     */
    @Transactional
    public HospitalDocumentsOverview upload(Long userId, MultipartFile file, HospitalDocumentType type,
                                            LocalDate issueDate, LocalDate expiryDate,
                                            Long replacesDocumentId, Long requestId) {
        Hospital hospital = requireMine(userId);
        LocalDate today = LocalDate.now();

        HospitalDocumentRequest request = null;
        if (requestId != null) {
            request = requestRepository.findById(requestId)
                    .filter(r -> r.getHospitalId().equals(hospital.getId()) && !r.isDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException("Document request", requestId));
            if (!request.isOutstanding()) {
                throw new BusinessException("This document request is no longer open", HttpStatus.CONFLICT);
            }
            if (type != null && type != request.getDocumentType()) {
                throw fieldError("documentType", "Must match the requested document type");
            }
            type = request.getDocumentType();
        }
        if (type == null) throw fieldError("documentType", "Select a document type");
        if (type == HospitalDocumentType.ADDITIONAL_REQUESTED && request == null) {
            throw fieldError("documentType", "Upload this against the administrator's request");
        }

        HospitalDocument replaced = null;
        if (replacesDocumentId != null) {
            replaced = documentRepository.findById(replacesDocumentId)
                    .filter(d -> d.getHospitalId().equals(hospital.getId()) && d.isCurrent() && !d.isDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException("Document", replacesDocumentId));
            if (replaced.getDocumentType() != type) {
                throw fieldError("documentType", "A replacement must be the same document type");
            }
            if (request == null && replaced.getDocumentRequestId() != null) {
                request = requestRepository.findById(replaced.getDocumentRequestId())
                        .filter(HospitalDocumentRequest::isOutstanding).orElse(null);
            }
        } else if (request != null) {
            replaced = first(documentRepository.findByDocumentRequestIdAndCurrentTrueAndDeletedFalse(request.getId()));
            if (replaced == null && type.singleSlot()) replaced = currentOfType(hospital, type);
        } else if (type.singleSlot()) {
            replaced = currentOfType(hospital, type);
            // A correction the admin asked for is answered by uploading that type again.
            final HospitalDocumentType t = type;
            request = requestRepository.findByHospitalIdAndStatusInAndDeletedFalseOrderByRequestedAtAsc(
                            hospital.getId(), EnumSet.of(HospitalDocumentRequest.Status.OPEN)).stream()
                    .filter(r -> r.getDocumentType() == t).findFirst().orElse(null);
        }

        validateDates(hospital, type, issueDate, expiryDate, today);
        if (file == null || file.isEmpty()) throw fieldError("file", "Choose a file to upload");
        long max = properties.maxFileSize().toBytes();
        if (file.getSize() > max) {
            throw new BusinessException("File exceeds the " + (max / (1024 * 1024)) + " MB limit", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String ext = DocumentContentValidator.extension(file);
        DocumentContentValidator.validate(file, ext);
        String sha256 = DocumentContentValidator.sha256(file);

        boolean wasFullyVerified = verificationService.isFullyVerified(hospital);
        String stored = fileStorageService.store(file, DocumentContentValidator.EXTENSIONS, max);
        HospitalDocument doc;
        try {
            doc = documentRepository.save(HospitalDocument.builder()
                    .hospitalId(hospital.getId())
                    .documentType(type)
                    .documentRequestId(request == null ? null : request.getId())
                    .originalFilename(DocumentContentValidator.displayName(file.getOriginalFilename()))
                    .storedFilename(stored)
                    .contentType(DocumentContentValidator.contentType(stored))
                    .fileSize(file.getSize())
                    .sha256(sha256)
                    .issueDate(issueDate)
                    .expiryDate(expiryDate)
                    .status(DocumentStatus.PENDING)
                    .current(true)
                    .uploadedBy(userId)
                    .uploadedAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            fileStorageService.delete(stored); // don't leave an orphan file if the row could not be saved
            throw e;
        }

        if (replaced != null) {
            replaced.setCurrent(false);
            replaced.setSupersededAt(LocalDateTime.now());
            replaced.setSupersededBy(doc.getId());
            documentRepository.save(replaced);
        }
        if (request != null) {
            request.setStatus(HospitalDocumentRequest.Status.SUBMITTED);
            requestRepository.save(request);
        }

        String label = request != null ? request.displayName() : type.label();
        boolean replacedApproved = replaced != null && replaced.getStatus() == DocumentStatus.VERIFIED;
        auditService.record(replaced != null ? "HOSPITAL_DOCUMENT_REPLACED" : "HOSPITAL_DOCUMENT_UPLOADED",
                "HospitalDocument", doc.getId(),
                hospital.getHospitalName() + " — " + label + " (" + doc.getOriginalFilename() + ", sha256 " + sha256 + ")"
                        + (replaced != null ? " replaces document #" + replaced.getId() : ""));
        notifier.admins("Hospital document awaiting review",
                hospital.getHospitalName() + (replaced != null ? " replaced " : " uploaded ") + label
                        + (replacedApproved ? " (previously approved — needs re-verification)" : "")
                        + ". Review it under Verifications.");

        Compliance after = verificationService.assess(hospital);
        if (wasFullyVerified && !after.fullyVerified()) {
            notifier.verificationLost(hospital, after, label + " was replaced and needs re-verification");
        }
        return overview(hospital);
    }

    /** Withdraw a current document. The file and row stay as history. */
    @Transactional
    public HospitalDocumentsOverview remove(Long userId, Long documentId) {
        Hospital hospital = requireMine(userId);
        HospitalDocument doc = documentRepository.findById(documentId)
                .filter(d -> d.getHospitalId().equals(hospital.getId()) && !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        if (!doc.isCurrent()) {
            throw new BusinessException("This document was already replaced or removed", HttpStatus.CONFLICT);
        }
        boolean wasFullyVerified = verificationService.isFullyVerified(hospital);
        doc.setCurrent(false);
        doc.setRemovedAt(LocalDateTime.now());
        doc.setRemovedBy(userId);
        documentRepository.save(doc);
        if (doc.getDocumentRequestId() != null) {
            requestRepository.findById(doc.getDocumentRequestId())
                    .filter(r -> r.getStatus() == HospitalDocumentRequest.Status.SUBMITTED)
                    .ifPresent(r -> { r.setStatus(HospitalDocumentRequest.Status.OPEN); requestRepository.save(r); });
        }
        auditService.record("HOSPITAL_DOCUMENT_REMOVED", "HospitalDocument", doc.getId(),
                hospital.getHospitalName() + " — " + doc.getDocumentType().label() + " (" + doc.getOriginalFilename() + ")");
        Compliance after = verificationService.assess(hospital);
        if (wasFullyVerified && !after.fullyVerified()) {
            notifier.verificationLost(hospital, after, doc.getDocumentType().label() + " was removed");
        }
        return overview(hospital);
    }

    /** One of the caller's own documents (current or historical). */
    @Transactional(readOnly = true)
    public DocumentFile myFile(Long userId, Long documentId) {
        Hospital hospital = requireMine(userId);
        HospitalDocument doc = documentRepository.findById(documentId)
                .filter(d -> d.getHospitalId().equals(hospital.getId()) && !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        return toFile(doc);
    }

    /** Any hospital's document — callers must already be authorised as ADMIN. */
    @Transactional(readOnly = true)
    public DocumentFile fileForAdmin(Long documentId) {
        HospitalDocument doc = documentRepository.findById(documentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        return toFile(doc);
    }

    /** A resolved private file plus what is needed to stream it. */
    public record DocumentFile(Path path, String contentType, String fileName) {}

    // ---------- helpers ----------

    HospitalDocumentsOverview overview(Hospital hospital) {
        LocalDate today = LocalDate.now();
        Compliance compliance = verificationService.assess(hospital);
        List<HospitalDocument> history = documentRepository.findByHospitalIdAndDeletedFalseOrderByUploadedAtDesc(hospital.getId());
        List<DocumentView> current = history.stream().filter(HospitalDocument::isCurrent)
                .map(d -> DocumentView.from(d, today)).toList();
        List<DocumentRequestView> requests = requestRepository
                .findByHospitalIdAndDeletedFalseOrderByRequestedAtDesc(hospital.getId()).stream()
                .map(DocumentRequestView::from).toList();
        return new HospitalDocumentsOverview(compliance, current, requests,
                history.stream().map(d -> DocumentView.from(d, today)).toList(), rules(hospital));
    }

    private UploadRules rules(Hospital hospital) {
        Map<HospitalDocumentType, HospitalDocumentRequirement> applicable = new EnumMap<>(HospitalDocumentType.class);
        verificationService.applicableRequirements(hospital).forEach(r -> applicable.put(r.getDocumentType(), r));
        List<UploadableType> types = new ArrayList<>();
        for (HospitalDocumentType t : HospitalDocumentType.values()) {
            if (t == HospitalDocumentType.ADDITIONAL_REQUESTED) continue; // only via a request
            HospitalDocumentRequirement r = applicable.get(t);
            types.add(new UploadableType(t, t.label(), !t.singleSlot(),
                    r != null && r.isMandatory(), r != null && r.isExpiryDateRequired()));
        }
        return new UploadRules(properties.maxFileSize().toBytes(), List.of("pdf", "jpg", "jpeg", "png"), types);
    }

    private void validateDates(Hospital hospital, HospitalDocumentType type, LocalDate issue, LocalDate expiry, LocalDate today) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (issue != null && issue.isAfter(today)) errors.put("issueDate", "Issue date cannot be in the future");
        if (expiry != null && issue != null && !expiry.isAfter(issue)) {
            errors.put("expiryDate", "Expiry date must be after the issue date");
        } else if (expiry != null && expiry.isBefore(today)) {
            errors.put("expiryDate", "This document has already expired — upload the current one");
        }
        boolean expiryRequired = verificationService.requirementFor(hospital, type)
                .map(HospitalDocumentRequirement::isExpiryDateRequired).orElse(false);
        if (expiryRequired && expiry == null) errors.put("expiryDate", "Expiry date is required for this document");
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
    }

    private HospitalDocument currentOfType(Hospital hospital, HospitalDocumentType type) {
        return first(documentRepository.findByHospitalIdAndDocumentTypeAndCurrentTrueAndDeletedFalse(hospital.getId(), type));
    }

    private static HospitalDocument first(List<HospitalDocument> docs) {
        return docs.isEmpty() ? null : docs.get(0);
    }

    private DocumentFile toFile(HospitalDocument doc) {
        return new DocumentFile(fileStorageService.load(doc.getStoredFilename()), doc.getContentType(),
                doc.getOriginalFilename());
    }

    private Hospital requireMine(Long userId) {
        return hospitalRepository.findByUserId(userId)
                .filter(h -> !h.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital profile not found. Complete your profile first."));
    }

    private static FieldValidationException fieldError(String field, String message) {
        return new FieldValidationException(Map.of(field, message));
    }
}
