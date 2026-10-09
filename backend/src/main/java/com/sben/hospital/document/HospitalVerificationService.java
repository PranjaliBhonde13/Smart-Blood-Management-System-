package com.sben.hospital.document;

import com.sben.common.exception.BusinessException;
import com.sben.hospital.Hospital;
import com.sben.hospital.document.dto.HospitalDocumentViews.Compliance;
import com.sben.hospital.document.dto.HospitalDocumentViews.RequirementView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides whether a hospital is <em>fully verified</em>: an administrator has
 * verified it, it has not been rejected, and every applicable mandatory
 * document (configured requirements plus outstanding administrator requests)
 * is approved and unexpired. Every backend check that restricts hospital
 * actions goes through {@link #isFullyVerified} / {@link #requireFullyVerified}.
 *
 * <p>Everything is computed from stored data on each call; expiry is evaluated
 * against today's date, so an expired document stops counting immediately.
 */
@Service
@RequiredArgsConstructor
public class HospitalVerificationService {

    private static final Set<HospitalDocumentRequest.Status> OUTSTANDING =
            EnumSet.of(HospitalDocumentRequest.Status.OPEN, HospitalDocumentRequest.Status.SUBMITTED);

    private final HospitalDocumentRepository documentRepository;
    private final HospitalDocumentRequirementRepository requirementRepository;
    private final HospitalDocumentRequestRepository requestRepository;
    private final HospitalDocumentProperties properties;

    @Transactional(readOnly = true)
    public boolean isFullyVerified(Hospital hospital) {
        // Cheap short-circuit: without the admin's approval nothing else matters.
        if (hospital == null || !hospital.isVerified() || hospital.getRejectedAt() != null || hospital.isDeleted()) {
            return false;
        }
        return assess(hospital).fullyVerified();
    }

    /** Throws 403 with a specific explanation unless the hospital is fully verified. */
    @Transactional(readOnly = true)
    public void requireFullyVerified(Hospital hospital) {
        if (hospital != null && !hospital.isVerified() && hospital.getRejectedAt() == null) {
            // Keep the existing wording for hospitals still awaiting an admin decision.
            throw new BusinessException("Your hospital must be verified by an administrator first", HttpStatus.FORBIDDEN);
        }
        Compliance c = assess(hospital);
        if (!c.fullyVerified()) {
            throw new BusinessException("Your hospital is not fully verified: " + c.message(), HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Applicable requirement per document type for this hospital: rules for its
     * registration category override the all-categories rule for the same type,
     * then the rule's condition (e.g. operates a blood bank) is applied.
     */
    @Transactional(readOnly = true)
    public List<HospitalDocumentRequirement> applicableRequirements(Hospital hospital) {
        Map<HospitalDocumentType, HospitalDocumentRequirement> byType = new LinkedHashMap<>();
        for (HospitalDocumentRequirement r : requirementRepository.findByActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc()) {
            if (r.getRegistrationCategory() != null && r.getRegistrationCategory() != hospital.getRegistrationCategory()) {
                continue;
            }
            HospitalDocumentRequirement existing = byType.get(r.getDocumentType());
            if (existing == null || (existing.getRegistrationCategory() == null && r.getRegistrationCategory() != null)) {
                byType.put(r.getDocumentType(), r);
            }
        }
        return byType.values().stream()
                .filter(r -> r.getAppliesWhen() != RequirementCondition.OPERATES_BLOOD_BANK || hospital.isOperatesBloodBank())
                .sorted(Comparator.comparingInt(HospitalDocumentRequirement::getSortOrder))
                .toList();
    }

    public Optional<HospitalDocumentRequirement> requirementFor(Hospital hospital, HospitalDocumentType type) {
        return applicableRequirements(hospital).stream().filter(r -> r.getDocumentType() == type).findFirst();
    }

    @Transactional(readOnly = true)
    public Compliance assess(Hospital hospital) {
        LocalDate today = LocalDate.now();
        List<HospitalDocument> current = documentRepository
                .findByHospitalIdAndCurrentTrueAndDeletedFalseOrderByUploadedAtDesc(hospital.getId());
        List<HospitalDocumentRequest> outstandingRequests = requestRepository
                .findByHospitalIdAndStatusInAndDeletedFalseOrderByRequestedAtAsc(hospital.getId(), OUTSTANDING);

        List<RequirementView> items = new ArrayList<>();
        for (HospitalDocumentRequirement r : applicableRequirements(hospital)) {
            List<HospitalDocument> docs = current.stream()
                    .filter(d -> d.getDocumentType() == r.getDocumentType()).toList();
            RequirementState state = aggregate(docs, today);
            items.add(new RequirementView(r.getId(), null, r.getDocumentType(), r.getDocumentType().label(),
                    r.getDescription(), r.isMandatory(), r.isExpiryDateRequired(), !r.getDocumentType().singleSlot(),
                    state, explain(state, r.isMandatory(), docs, today), ids(docs)));
        }
        for (HospitalDocumentRequest req : outstandingRequests) {
            List<HospitalDocument> docs = current.stream()
                    .filter(d -> req.getId().equals(d.getDocumentRequestId())).toList();
            RequirementState state = aggregate(docs, today);
            String explanation = "Requested by an administrator: " + req.getReason()
                    + (state == RequirementState.NOT_SUBMITTED ? "" : " — " + explain(state, true, docs, today));
            items.add(new RequirementView(null, req.getId(), req.getDocumentType(), req.displayName(),
                    req.getReason(), true, false, false, state, explanation, ids(docs)));
        }

        List<RequirementView> unsatisfied = items.stream()
                .filter(i -> i.mandatory() && i.state() != RequirementState.VERIFIED).toList();
        boolean documentsComplete = unsatisfied.isEmpty();
        List<String> missingProfile = missingProfileFields(hospital);
        boolean profileComplete = missingProfile.isEmpty();
        boolean adminVerified = hospital.isVerified() && hospital.getRejectedAt() == null;

        LocalDate grace = properties.legacyGraceUntil();
        boolean inGrace = !documentsComplete && adminVerified && grace != null && !today.isAfter(grace)
                && unsatisfied.stream().allMatch(i -> i.requestId() == null
                        && (i.state() == RequirementState.NOT_SUBMITTED || i.state() == RequirementState.PENDING));
        boolean fullyVerified = adminVerified && !hospital.isDeleted() && (documentsComplete || inGrace);

        int pendingReview = (int) current.stream()
                .filter(d -> d.effectiveStatus(today) == DocumentStatus.PENDING).count();
        List<String> outstanding = unsatisfied.stream().map(RequirementView::label).distinct().toList();

        HospitalVerificationStatus status;
        String message;
        if (fullyVerified) {
            status = HospitalVerificationStatus.VERIFIED;
            message = inGrace
                    ? "Verified during the document transition period. Upload and get approval for "
                      + String.join(", ", outstanding) + " by " + grace + " to stay active."
                    : "Verified — all mandatory documents are approved and valid.";
        } else if (hospital.getRejectedAt() != null) {
            status = HospitalVerificationStatus.ACTION_REQUIRED;
            message = "Hospital verification was rejected: " + hospital.getRejectionReason()
                      + ". Correct your profile and save it to resubmit.";
        } else if (unsatisfied.stream().anyMatch(i -> i.state() == RequirementState.REJECTED
                || (i.requestId() != null && i.state() == RequirementState.NOT_SUBMITTED))) {
            status = HospitalVerificationStatus.ACTION_REQUIRED;
            message = "Some documents were rejected or requested by an administrator: "
                      + labels(unsatisfied, i -> i.state() == RequirementState.REJECTED
                        || (i.requestId() != null && i.state() == RequirementState.NOT_SUBMITTED)) + ".";
        } else if (unsatisfied.stream().anyMatch(i -> i.state() == RequirementState.EXPIRED)) {
            status = HospitalVerificationStatus.VERIFICATION_EXPIRED;
            message = "Expired documents are no longer valid: "
                      + labels(unsatisfied, i -> i.state() == RequirementState.EXPIRED) + ". Upload renewed documents.";
        } else if (!profileComplete) {
            status = HospitalVerificationStatus.PROFILE_INCOMPLETE;
            message = "Complete your hospital profile: " + String.join(", ", missingProfile) + ".";
        } else if (unsatisfied.stream().anyMatch(i -> i.state() == RequirementState.NOT_SUBMITTED)) {
            status = HospitalVerificationStatus.DOCUMENTS_PENDING;
            message = "Upload the required documents: "
                      + labels(unsatisfied, i -> i.state() == RequirementState.NOT_SUBMITTED) + ".";
        } else if (!documentsComplete) {
            status = HospitalVerificationStatus.DOCUMENTS_PENDING;
            message = "Your documents are awaiting administrator review.";
        } else {
            status = HospitalVerificationStatus.DOCUMENTS_PENDING;
            message = "All mandatory documents are approved. Awaiting final administrator approval of your hospital.";
        }

        return new Compliance(status, message, fullyVerified, adminVerified, documentsComplete, profileComplete,
                missingProfile, inGrace, inGrace ? grace : null, pendingReview, outstanding, items);
    }

    /** Profile fields an administrator needs to verify a hospital (kept separate from documents). */
    public static List<String> missingProfileFields(Hospital h) {
        List<String> missing = new ArrayList<>();
        if (blank(h.getHospitalName())) missing.add("hospital name");
        if (blank(h.getLicenseNumber())) missing.add("license number");
        if (h.getRegistrationCategory() == null) missing.add("registration category");
        if (blank(h.getPhone())) missing.add("phone");
        if (blank(h.getAddress())) missing.add("address");
        if (blank(h.getCity())) missing.add("city");
        if (blank(h.getState())) missing.add("state");
        if (blank(h.getPincode())) missing.add("pincode");
        return missing;
    }

    // ---------- helpers ----------

    /** Best state across a requirement's current documents (one valid approval is enough). */
    static RequirementState aggregate(List<HospitalDocument> docs, LocalDate today) {
        if (docs.isEmpty()) return RequirementState.NOT_SUBMITTED;
        Set<DocumentStatus> states = docs.stream().map(d -> d.effectiveStatus(today))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentStatus.class)));
        if (states.contains(DocumentStatus.VERIFIED)) return RequirementState.VERIFIED;
        if (states.contains(DocumentStatus.PENDING)) return RequirementState.PENDING;
        if (states.contains(DocumentStatus.REJECTED)) return RequirementState.REJECTED;
        return RequirementState.EXPIRED;
    }

    private static String explain(RequirementState state, boolean mandatory, List<HospitalDocument> docs, LocalDate today) {
        return switch (state) {
            case NOT_SUBMITTED -> mandatory ? "Required — not uploaded yet." : "Optional.";
            case PENDING -> "Uploaded — awaiting administrator review.";
            case VERIFIED -> docs.stream().filter(d -> d.isValid(today)).map(HospitalDocument::getExpiryDate)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder())
                    .map(d -> "Approved — valid until " + d + ".").orElse("Approved.");
            case REJECTED -> "Rejected: " + docs.stream().filter(d -> d.getStatus() == DocumentStatus.REJECTED)
                    .map(HospitalDocument::getReviewRemarks).filter(Objects::nonNull).findFirst()
                    .orElse("no remarks given") + ". Upload a corrected document.";
            case EXPIRED -> "Expired" + docs.stream().map(HospitalDocument::getExpiryDate).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).map(d -> " on " + d).orElse("")
                    + " — no longer valid. Upload the renewed document.";
        };
    }

    private static String labels(List<RequirementView> items, java.util.function.Predicate<RequirementView> p) {
        return items.stream().filter(p).map(RequirementView::label).distinct().collect(Collectors.joining(", "));
    }

    private static List<Long> ids(List<HospitalDocument> docs) {
        return docs.stream().map(HospitalDocument::getId).toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
