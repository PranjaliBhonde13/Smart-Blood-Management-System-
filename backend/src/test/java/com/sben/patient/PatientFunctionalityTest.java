package com.sben.patient;

import com.sben.audit.AuditService;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.notification.NotificationService;
import com.sben.patient.document.*;
import com.sben.patient.dto.PatientRequest;
import com.sben.patient.dto.PatientResponse;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Patient profile + verification documents, with an in-memory document store so
 * replacement history and the two independent verification tracks are exercised.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientFunctionalityTest {

    private static final byte[] PDF = "%PDF-1.4 test".getBytes();

    @Mock private PatientRepository repository;
    @Mock private PatientDocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @TempDir Path uploadDir;

    private PatientService service;
    private PatientVerificationService verification;
    private final List<PatientDocument> docs = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private User user;
    private Patient patient;

    @BeforeEach
    void setUp() throws Exception {
        FileStorageService storage = new FileStorageService(uploadDir.toString());
        var init = FileStorageService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(storage);
        service = new PatientService(repository, documentRepository, userRepository, storage,
                auditService, notificationService, DataSize.ofMegabytes(5));
        verification = new PatientVerificationService(repository, documentRepository, service,
                auditService, notificationService);

        user = User.builder().id(100L).fullName("Asha Rao").email("asha@example.com").phone("+919876543210")
                .role(Role.PATIENT).emailVerified(true).build();
        patient = Patient.builder().id(1L).userId(100L).build();
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(userRepository.findByRoleAndDeletedFalse(any(), any())).thenReturn(Page.empty());
        when(repository.findByUserId(100L)).thenAnswer(i -> Optional.ofNullable(patient));
        when(repository.findById(1L)).thenAnswer(i -> Optional.ofNullable(patient));
        when(repository.save(any(Patient.class))).thenAnswer(i -> { patient = i.getArgument(0); return patient; });

        // in-memory document store
        when(documentRepository.save(any(PatientDocument.class))).thenAnswer(i -> {
            PatientDocument d = i.getArgument(0);
            if (d.getId() == null) { d.setId(ids.getAndIncrement()); docs.add(d); }
            return d;
        });
        when(documentRepository.findById(anyLong())).thenAnswer(i ->
                docs.stream().filter(d -> d.getId().equals(i.getArgument(0))).findFirst());
        when(documentRepository.findFirstByPatientIdAndCategoryAndCurrentTrueAndDeletedFalse(anyLong(), any()))
                .thenAnswer(i -> docs.stream().filter(d -> d.getPatientId().equals(i.getArgument(0))
                        && d.getCategory() == i.getArgument(1) && d.isCurrent()).findFirst());
        when(documentRepository.findByPatientIdAndDeletedFalseOrderByUploadedAtDesc(anyLong()))
                .thenAnswer(i -> docs.stream().filter(d -> d.getPatientId().equals(i.getArgument(0))).toList());
    }

    private static PatientRequest request(String name, LocalDate dob, String group) {
        return new PatientRequest(name, dob, group, "", "98765 43210", "12 MG Road", "Pune", "Maharashtra", "411001",
                null, null, "", "", true, true);
    }

    private PatientResponse saveDefaults() {
        return service.upsertMyProfile(100L, request("Asha Rao", LocalDate.of(1990, 5, 1), "A+"));
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private PatientDocument currentDoc(DocumentCategory c) {
        return docs.stream().filter(d -> d.getCategory() == c && d.isCurrent()).findFirst().orElseThrow();
    }

    // ---------- profile ----------

    @Test
    void getMyProfile_Throws_WhenProfileDoesNotExist() {
        patient = null;
        assertThrows(ResourceNotFoundException.class, () -> service.getMyProfile(100L));
    }

    @Test
    void upsert_UpdatesAccountAndIsIncompleteUntilDocumentsUploaded() {
        PatientResponse res = saveDefaults();
        assertEquals("+919876543210", user.getPhone(), "phone normalised");
        assertEquals("A+", res.bloodGroup());
        assertNotNull(res.age());
        assertFalse(res.phoneVerified(), "no SMS verification exists, so never claimed");
        assertEquals(PatientVerificationStatus.PROFILE_INCOMPLETE, res.verificationStatus());
        assertEquals(TrackStatus.NOT_SUBMITTED, res.identity().status());
        assertFalse(res.bloodGroupVerified());
        assertNotNull(res.privacyAcknowledgedAt());
    }

    @Test
    void upsert_RejectsHalfEmergencyContactAndOwnNumber() {
        PatientRequest half = new PatientRequest("Asha", LocalDate.of(1990, 1, 1), "A+", null, "9876543210",
                "a", "b", "c", "411001", null, null, "Ravi", "", null, null);
        FieldValidationException ex = assertThrows(FieldValidationException.class, () -> service.upsertMyProfile(100L, half));
        assertTrue(ex.getFieldErrors().containsKey("emergencyContactPhone"));

        PatientRequest same = new PatientRequest("Asha", LocalDate.of(1990, 1, 1), "A+", null, "9876543210",
                "a", "b", "c", "411001", null, null, "Ravi", "+91 9876543210", null, null);
        assertThrows(FieldValidationException.class, () -> service.upsertMyProfile(100L, same));
    }

    // ---------- documents ----------

    @Test
    void upload_RequiresPrivacyAcknowledgement() {
        service.upsertMyProfile(100L, new PatientRequest("Asha", LocalDate.of(1990, 1, 1), "A+", null, "9876543210",
                "a", "b", "c", "411001", null, null, null, null, null, false));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadDocument(100L, PatientDocumentType.PASSPORT, pdf("p.pdf")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void upload_RejectsWrongTypeAndSpoofedContent() {
        saveDefaults();
        assertThrows(BusinessException.class, () -> service.uploadDocument(100L, PatientDocumentType.PASSPORT,
                new MockMultipartFile("file", "id.docx", "application/msword", PDF)));
        assertThrows(BusinessException.class, () -> service.uploadDocument(100L, PatientDocumentType.PASSPORT,
                new MockMultipartFile("file", "id.pdf", "application/pdf", "not a pdf".getBytes())));
        assertTrue(docs.isEmpty());
    }

    @Test
    void upload_ReplacementKeepsHistoryAndStartsPending() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.AADHAAR, pdf("aadhaar.pdf"));
        PatientResponse res = service.uploadDocument(100L, PatientDocumentType.PASSPORT, pdf("../x/passport.pdf"));

        assertEquals(2, docs.size(), "old document kept as history");
        assertFalse(docs.get(0).isCurrent());
        assertEquals(PatientDocumentType.PASSPORT, res.identity().document().documentType());
        assertEquals("passport.pdf", res.identity().document().fileName());
        assertEquals(TrackStatus.PENDING, res.identity().status());
        assertEquals(64, docs.get(1).getSha256().length());
        verify(auditService).record(eq("PATIENT_DOCUMENT_REPLACED"), any(), any(), any());
    }

    @Test
    void ownerCannotFetchAnotherPatientsDocument() {
        docs.add(PatientDocument.builder().id(99L).patientId(2L).category(DocumentCategory.IDENTITY)
                .storedFilename("x.pdf").contentType("application/pdf").current(true).build());
        saveDefaults();
        assertThrows(ResourceNotFoundException.class, () -> service.myDocumentFile(100L, 99L));
    }

    // ---------- independent verification tracks ----------

    @Test
    void identityVerified_DoesNotVerifyBloodGroup() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.VOTER_ID, pdf("id.pdf"));
        service.uploadDocument(100L, PatientDocumentType.LAB_REPORT, pdf("lab.pdf"));
        verification.review(currentDoc(DocumentCategory.IDENTITY).getId(),
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, null), 1L);

        PatientResponse res = service.getMyProfile(100L);
        assertEquals(TrackStatus.VERIFIED, res.identity().status());
        assertEquals(TrackStatus.PENDING, res.bloodGroupVerification().status());
        assertFalse(res.bloodGroupVerified());
        assertEquals(PatientVerificationStatus.PENDING_VERIFICATION, res.verificationStatus());
        assertFalse(service.profileBloodGroup(patient).verified());
    }

    @Test
    void bloodGroupApproval_RequiresTheGroupReadOnTheDocument() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.LAB_REPORT, pdf("lab.pdf"));
        Long id = currentDoc(DocumentCategory.BLOOD_GROUP).getId();

        assertThrows(BusinessException.class, () -> verification.review(id,
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, null), 1L));
        BusinessException mismatch = assertThrows(BusinessException.class, () -> verification.review(id,
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, "B+"), 1L));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, mismatch.getStatus());

        verification.review(id, new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, "A+"), 1L);
        PatientResponse res = service.getMyProfile(100L);
        assertTrue(res.bloodGroupVerified());
        assertEquals("A+", res.verifiedBloodGroup());
        assertTrue(service.profileBloodGroup(patient).verified());
    }

    @Test
    void bothTracksVerified_IsVerified_AndChangingGroupReopensOnlyBloodGroup() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.PAN_CARD, pdf("pan.pdf"));
        service.uploadDocument(100L, PatientDocumentType.HOSPITAL_RECORD, pdf("rec.pdf"));
        verification.review(currentDoc(DocumentCategory.IDENTITY).getId(),
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, null), 1L);
        verification.review(currentDoc(DocumentCategory.BLOOD_GROUP).getId(),
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, "A+"), 1L);
        assertEquals(PatientVerificationStatus.VERIFIED, service.getMyProfile(100L).verificationStatus());

        PatientResponse res = service.upsertMyProfile(100L, request("Asha Rao", LocalDate.of(1990, 5, 1), "B+"));
        assertEquals(TrackStatus.VERIFIED, res.identity().status(), "identity untouched");
        assertEquals(TrackStatus.PENDING, res.bloodGroupVerification().status(), "blood group back to review");
        assertFalse(res.bloodGroupVerified());
        assertEquals(PatientVerificationStatus.PENDING_VERIFICATION, res.verificationStatus());
    }

    @Test
    void changingNameReopensIdentity() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.PASSPORT, pdf("p.pdf"));
        verification.review(currentDoc(DocumentCategory.IDENTITY).getId(),
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, null), 1L);

        PatientResponse res = service.upsertMyProfile(100L, request("Asha R. Kulkarni", LocalDate.of(1990, 5, 1), "A+"));
        assertEquals(TrackStatus.PENDING, res.identity().status());
    }

    @Test
    void rejectAndRequestChanges_RequireRemarks_AndDriveOverallStatus() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.DRIVING_LICENCE, pdf("dl.pdf"));
        service.uploadDocument(100L, PatientDocumentType.LAB_REPORT, pdf("lab.pdf"));
        Long idDoc = currentDoc(DocumentCategory.IDENTITY).getId();
        Long bgDoc = currentDoc(DocumentCategory.BLOOD_GROUP).getId();

        assertThrows(BusinessException.class, () -> verification.review(idDoc,
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.REJECT, " ", null), 1L));
        verification.review(bgDoc, new ReviewDocumentRequest(ReviewDocumentRequest.Decision.REQUEST_CHANGES,
                "Report is cut off — upload the full page", null), 1L);
        PatientResponse res = service.getMyProfile(100L);
        assertEquals(PatientVerificationStatus.ACTION_REQUIRED, res.verificationStatus());
        assertEquals("Report is cut off — upload the full page", res.bloodGroupVerification().document().reviewRemarks());

        verification.review(idDoc, new ReviewDocumentRequest(ReviewDocumentRequest.Decision.REJECT,
                "Name does not match", null), 1L);
        assertEquals(PatientVerificationStatus.REJECTED, service.getMyProfile(100L).verificationStatus());
    }

    @Test
    void cannotReviewAReplacedDocument() {
        saveDefaults();
        service.uploadDocument(100L, PatientDocumentType.PASSPORT, pdf("a.pdf"));
        Long old = currentDoc(DocumentCategory.IDENTITY).getId();
        service.uploadDocument(100L, PatientDocumentType.PASSPORT, pdf("b.pdf"));
        BusinessException ex = assertThrows(BusinessException.class, () -> verification.review(old,
                new ReviewDocumentRequest(ReviewDocumentRequest.Decision.APPROVE, null, null), 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }
}
