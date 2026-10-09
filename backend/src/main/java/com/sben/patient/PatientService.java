package com.sben.patient;

import com.sben.audit.AuditService;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.notification.Notification;
import com.sben.notification.NotificationService;
import com.sben.patient.document.*;
import com.sben.patient.dto.DocumentView;
import com.sben.patient.dto.PatientRequest;
import com.sben.patient.dto.PatientResponse;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Manages the caller's own patient profile and verification documents.
 *
 * <p>Verification is never set here: uploads start PENDING and only
 * {@link PatientVerificationService} (admin-only) records decisions. Identity
 * and blood group are separate tracks. When a detail a decision was based on
 * changes (name/date of birth for identity, blood group for blood group), the
 * affected document goes back to PENDING for re-review.
 */
@Service
public class PatientService {

    static final Set<String> DOCUMENT_TYPES = Set.of("pdf", "png", "jpg", "jpeg");

    private final PatientRepository repository;
    private final PatientDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final long maxDocumentBytes;

    public PatientService(PatientRepository repository, PatientDocumentRepository documentRepository,
                          UserRepository userRepository, FileStorageService fileStorageService,
                          AuditService auditService, NotificationService notificationService,
                          @Value("${app.patient-documents.max-file-size:5MB}") DataSize maxDocumentSize) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.maxDocumentBytes = maxDocumentSize.toBytes();
    }

    // ---------- profile ----------

    @Transactional(readOnly = true)
    public PatientResponse getMyProfile(Long userId) {
        return toResponse(requireMine(userId));
    }

    @Transactional
    public PatientResponse upsertMyProfile(Long userId, PatientRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        validateCrossField(req);

        Patient patient = repository.findByUserId(userId)
                .orElseGet(() -> Patient.builder().userId(userId).build());

        String fullName = req.fullName().trim().replaceAll("\\s+", " ");
        boolean identityDetailsChanged = !fullName.equals(user.getFullName())
                || !Objects.equals(req.dateOfBirth(), patient.getDateOfBirth());
        boolean bloodGroupChanged = !req.bloodGroup().equals(patient.getBloodGroup());

        user.setFullName(fullName);
        user.setPhone(normalisePhone(req.phone()));
        userRepository.save(user);

        patient.setDateOfBirth(req.dateOfBirth());
        patient.setAge(null); // age now comes from the date of birth
        patient.setBloodGroup(req.bloodGroup());
        patient.setGender(blankToNull(req.gender()));
        patient.setAddress(req.address().trim());
        patient.setCity(req.city().trim());
        patient.setState(req.state().trim());
        patient.setPincode(req.pincode().trim());
        patient.setLatitude(req.latitude());
        patient.setLongitude(req.longitude());
        patient.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        patient.setEmergencyContact(normalisePhone(req.emergencyContactPhone()));
        if (req.notificationConsent() != null && !req.notificationConsent().equals(patient.getNotificationConsent())) {
            patient.setNotificationConsent(req.notificationConsent());
            patient.setConsentUpdatedAt(now());
        }
        if (Boolean.TRUE.equals(req.privacyAcknowledged()) && patient.getPrivacyAcknowledgedAt() == null) {
            patient.setPrivacyAcknowledgedAt(now());
        }
        Patient saved = repository.save(patient);

        if (identityDetailsChanged) reopenIfDecided(saved, DocumentCategory.IDENTITY, "name or date of birth changed");
        if (bloodGroupChanged) reopenIfDecided(saved, DocumentCategory.BLOOD_GROUP, "blood group changed to " + req.bloodGroup());
        return toResponse(saved, user);
    }

    // ---------- documents ----------

    /**
     * Upload (or replace) the document for one track. The previous current
     * document is kept as history; the new one always starts PENDING.
     */
    @Transactional
    public PatientResponse uploadDocument(Long userId, PatientDocumentType type, MultipartFile file) {
        Patient patient = requireMine(userId);
        if (patient.getPrivacyAcknowledgedAt() == null) {
            throw new BusinessException("Please read and accept the privacy notice before uploading documents",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        DocumentCategory category = type.category();
        String sha256 = sha256(file);
        String stored = fileStorageService.store(file, DOCUMENT_TYPES, maxDocumentBytes);

        Optional<PatientDocument> previous = documentRepository
                .findFirstByPatientIdAndCategoryAndCurrentTrueAndDeletedFalse(patient.getId(), category);
        previous.ifPresent(p -> {
            p.setCurrent(false);
            p.setSupersededAt(now());
            documentRepository.save(p);
        });
        PatientDocument doc = documentRepository.save(PatientDocument.builder()
                .patientId(patient.getId())
                .category(category)
                .documentType(type)
                .originalFilename(displayName(file.getOriginalFilename()))
                .storedFilename(stored)
                .contentType(contentType(stored))
                .fileSize(file.getSize())
                .sha256(sha256)
                .status(PatientDocumentStatus.PENDING)
                .current(true)
                .uploadedBy(userId)
                .uploadedAt(now())
                .build());

        auditService.record(previous.isPresent() ? "PATIENT_DOCUMENT_REPLACED" : "PATIENT_DOCUMENT_UPLOADED",
                "PatientDocument", doc.getId(), category + " / " + type + " for patient " + patient.getId());
        notifyAdmins("Patient document awaiting review",
                "A patient uploaded a " + type.label().toLowerCase() + ". Review it under Patient verifications.");
        return toResponse(patient);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> myDocumentHistory(Long userId) {
        Patient patient = requireMine(userId);
        return documentRepository.findByPatientIdAndDeletedFalseOrderByUploadedAtDesc(patient.getId())
                .stream().map(DocumentView::from).toList();
    }

    /** The caller's own document file (any version they uploaded). */
    @Transactional(readOnly = true)
    public StoredFile myDocumentFile(Long userId, Long documentId) {
        Patient patient = requireMine(userId);
        PatientDocument doc = documentRepository.findById(documentId)
                .filter(d -> !d.isDeleted() && d.getPatientId().equals(patient.getId()))
                // Same 404 for "not yours" and "missing": never confirm another patient's document exists.
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        return fileOf(doc);
    }

    StoredFile fileOf(PatientDocument doc) {
        return new StoredFile(fileStorageService.load(doc.getStoredFilename()), doc.getContentType(),
                doc.getOriginalFilename());
    }

    /** A resolved private file plus the headers needed to stream it. */
    public record StoredFile(Path path, String contentType, String downloadName) {}

    // ---------- blood group for requests ----------

    /**
     * The blood group the patient's own requests must use, and whether it is
     * backed by a verified document. Read from the database on every call.
     */
    @Transactional(readOnly = true)
    public ProfileBloodGroup profileBloodGroup(Patient patient) {
        PatientDocument doc = current(patient.getId(), DocumentCategory.BLOOD_GROUP);
        return new ProfileBloodGroup(patient.getBloodGroup(), isBloodGroupVerified(patient, doc));
    }

    public record ProfileBloodGroup(String bloodGroup, boolean verified) {}

    // ---------- response building ----------

    public PatientResponse toResponse(Patient p) {
        User user = userRepository.findById(p.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", p.getUserId()));
        return toResponse(p, user);
    }

    PatientResponse toResponse(Patient p, User user) {
        PatientDocument identity = current(p.getId(), DocumentCategory.IDENTITY);
        PatientDocument blood = current(p.getId(), DocumentCategory.BLOOD_GROUP);
        List<String> missing = missingFields(p, user);
        boolean bloodVerified = isBloodGroupVerified(p, blood);
        return new PatientResponse(p.getId(), p.getUserId(),
                user.getFullName(), user.getEmail(), user.isEmailVerified(), user.getPhone(), false,
                p.getDateOfBirth(), p.currentAge(), p.getBloodGroup(), p.getGender(),
                p.getAddress(), p.getCity(), p.getState(), p.getPincode(), p.getLatitude(), p.getLongitude(),
                p.getEmergencyContactName(), p.getEmergencyContact(),
                p.getNotificationConsent(), p.getConsentUpdatedAt(), p.getPrivacyAcknowledgedAt(),
                overallStatus(missing, identity, blood, bloodVerified),
                new PatientResponse.Track(TrackStatus.of(identity), identity == null ? null : DocumentView.from(identity)),
                new PatientResponse.Track(TrackStatus.of(blood), blood == null ? null : DocumentView.from(blood)),
                bloodVerified,
                blood != null && blood.getStatus() == PatientDocumentStatus.VERIFIED ? blood.getConfirmedBloodGroup() : null,
                missing);
    }

    static PatientVerificationStatus overallStatus(List<String> missing, PatientDocument identity,
                                                   PatientDocument blood, boolean bloodVerified) {
        TrackStatus id = TrackStatus.of(identity);
        TrackStatus bg = TrackStatus.of(blood);
        if (id == TrackStatus.REJECTED || bg == TrackStatus.REJECTED) return PatientVerificationStatus.REJECTED;
        if (id == TrackStatus.ACTION_REQUIRED || bg == TrackStatus.ACTION_REQUIRED
                || (bg == TrackStatus.VERIFIED && !bloodVerified)) {
            return PatientVerificationStatus.ACTION_REQUIRED;
        }
        if (!missing.isEmpty() || id == TrackStatus.NOT_SUBMITTED || bg == TrackStatus.NOT_SUBMITTED) {
            return PatientVerificationStatus.PROFILE_INCOMPLETE;
        }
        if (id == TrackStatus.VERIFIED && bloodVerified) return PatientVerificationStatus.VERIFIED;
        return PatientVerificationStatus.PENDING_VERIFICATION;
    }

    static boolean isBloodGroupVerified(Patient p, PatientDocument blood) {
        return blood != null && blood.getStatus() == PatientDocumentStatus.VERIFIED
                && p.getBloodGroup() != null && p.getBloodGroup().equals(blood.getConfirmedBloodGroup());
    }

    static List<String> missingFields(Patient p, User u) {
        List<String> missing = new ArrayList<>();
        if (isBlank(u.getFullName())) missing.add("fullName");
        if (p.getDateOfBirth() == null) missing.add("dateOfBirth");
        if (isBlank(p.getBloodGroup())) missing.add("bloodGroup");
        if (isBlank(u.getPhone())) missing.add("phone");
        if (isBlank(p.getAddress())) missing.add("address");
        if (isBlank(p.getCity())) missing.add("city");
        if (isBlank(p.getState())) missing.add("state");
        if (isBlank(p.getPincode())) missing.add("pincode");
        return missing;
    }

    // ---------- helpers ----------

    private PatientDocument current(Long patientId, DocumentCategory category) {
        return documentRepository.findFirstByPatientIdAndCategoryAndCurrentTrueAndDeletedFalse(patientId, category)
                .orElse(null);
    }

    /** A decision was based on details that changed: send the document back for re-review. */
    private void reopenIfDecided(Patient patient, DocumentCategory category, String why) {
        PatientDocument doc = current(patient.getId(), category);
        if (doc == null || doc.getStatus() == PatientDocumentStatus.PENDING) return;
        doc.reopenForReview();
        documentRepository.save(doc);
        auditService.record("PATIENT_DOCUMENT_REOPENED", "PatientDocument", doc.getId(),
                category + " re-review: " + why);
        notifyAdmins("Patient document awaiting re-review",
                "A patient's " + category.name().toLowerCase().replace('_', ' ') + " details changed ("
                        + why + "). Review it under Patient verifications.");
    }

    private void validateCrossField(PatientRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (req.dateOfBirth() != null && req.dateOfBirth().isBefore(LocalDate.now().minusYears(120))) {
            errors.put("dateOfBirth", "Enter a valid date of birth");
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            errors.put(req.latitude() == null ? "latitude" : "longitude", "Enter both latitude and longitude, or neither");
        }
        boolean hasName = !isBlank(req.emergencyContactName());
        boolean hasPhone = !isBlank(req.emergencyContactPhone());
        if (hasName != hasPhone) {
            errors.put(hasName ? "emergencyContactPhone" : "emergencyContactName",
                    "Enter both the emergency contact's name and phone, or neither");
        }
        if (hasPhone && !isBlank(req.phone())
                && normalisePhone(req.phone()).equals(normalisePhone(req.emergencyContactPhone()))) {
            errors.put("emergencyContactPhone", "Use a different number from your own");
        }
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
    }

    private Patient requireMine(Long userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found. Complete your profile."));
    }

    private void notifyAdmins(String title, String message) {
        for (User admin : userRepository.findByRoleAndDeletedFalse(Role.ADMIN, Pageable.unpaged())) {
            if (admin.isEnabled()) {
                notificationService.notify(admin.getId(), title, message, Notification.Type.VERIFICATION);
            }
        }
    }

    private static String sha256(MultipartFile file) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException("Could not read the uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    /** Keep only the base name, strip control/quote/path characters, cap the length. */
    static String displayName(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "" : original));
        if (name == null || name.isBlank()) return "document";
        name = name.replaceAll("[\\p{Cntrl}\"\\\\/<>]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String contentType(String storedName) {
        String lower = storedName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    static String normalisePhone(String phone) {
        if (isBlank(phone)) return null;
        String digits = phone.replaceAll("\\D", "");
        return "+91" + digits.substring(digits.length() - 10);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    /** Whole seconds, matching the DATETIME columns, so a saved value reads back unchanged. */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
