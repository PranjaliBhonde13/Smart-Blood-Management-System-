package com.sben.hospital.document;

import com.sben.audit.AuditService;
import com.sben.bloodrequest.BloodRequest;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodrequest.BloodRequestService;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodrequest.Urgency;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.donation.DonationHistoryRepository;
import com.sben.donor.DonorRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalCategory;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.dto.HospitalDocumentViews.*;
import com.sben.notification.NotificationService;
import com.sben.patient.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end hospital document workflow through the real services (upload,
 * validation, admin review, replacement, expiry, admin requests) and the
 * backend restriction on unverified hospitals. Repositories are mocks backed
 * by an in-memory store; files go to a temporary private upload directory.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HospitalDocumentWorkflowTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer\n%%EOF\n".getBytes();
    private static final long HOSPITAL_USER = 102L;
    private static final long OTHER_HOSPITAL_USER = 202L;
    private static final long ADMIN = 1L;

    @Mock private HospitalRepository hospitalRepository;
    @Mock private HospitalDocumentRepository documentRepository;
    @Mock private HospitalDocumentRequestRepository requestRepository;
    @Mock private HospitalDocumentRequirementRepository requirementRepository;
    @Mock private HospitalDocumentNotifier notifier;
    @Mock private AuditService auditService;
    @TempDir Path uploadDir;

    private final Map<Long, HospitalDocument> docs = new LinkedHashMap<>();
    private final Map<Long, HospitalDocumentRequest> requests = new LinkedHashMap<>();
    private final List<HospitalDocumentRequirement> rules = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    private Hospital hospital;
    private Hospital otherHospital;
    private HospitalVerificationService verification;
    private HospitalDocumentService documents;
    private HospitalDocumentReviewService review;
    private HospitalDocumentExpiryJob expiryJob;

    @BeforeEach
    void setUp() throws Exception {
        FileStorageService storage = new FileStorageService(uploadDir.toString());
        var init = FileStorageService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(storage);

        hospital = completeHospital(1L, HOSPITAL_USER, "City General Hospital");
        otherHospital = completeHospital(2L, OTHER_HOSPITAL_USER, "Other Hospital");
        when(hospitalRepository.findByUserId(HOSPITAL_USER)).thenReturn(Optional.of(hospital));
        when(hospitalRepository.findByUserId(OTHER_HOSPITAL_USER)).thenReturn(Optional.of(otherHospital));
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(hospital));
        when(hospitalRepository.findById(2L)).thenReturn(Optional.of(otherHospital));

        // Default rules from V9.
        rules.add(rule(1L, HospitalDocumentType.REGISTRATION_CERTIFICATE, null, RequirementCondition.ALWAYS, true, false));
        rules.add(rule(2L, HospitalDocumentType.OPERATING_LICENSE, null, RequirementCondition.ALWAYS, true, false));
        rules.add(rule(3L, HospitalDocumentType.BLOOD_BANK_LICENSE, null, RequirementCondition.OPERATES_BLOOD_BANK, true, true));
        rules.add(rule(4L, HospitalDocumentType.ACCREDITATION_CERTIFICATE, null, RequirementCondition.ALWAYS, false, false));
        when(requirementRepository.findByActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc()).thenAnswer(inv -> List.copyOf(rules));
        when(requirementRepository.findByDeletedFalseOrderBySortOrderAscIdAsc()).thenAnswer(inv -> List.copyOf(rules));
        stubDocumentStore();
        stubRequestStore();

        verification = new HospitalVerificationService(documentRepository, requirementRepository, requestRepository,
                HospitalDocumentProperties.defaults());
        documents = new HospitalDocumentService(hospitalRepository, documentRepository, requestRepository, verification,
                notifier, storage, auditService, HospitalDocumentProperties.defaults());
        review = new HospitalDocumentReviewService(hospitalRepository, documentRepository, requestRepository,
                requirementRepository, verification, notifier, auditService);
        expiryJob = new HospitalDocumentExpiryJob(documentRepository, hospitalRepository, verification, notifier,
                auditService, HospitalDocumentProperties.defaults());
    }

    // ---------- uploads & validation ----------

    @Test
    void upload_ValidPdf_IsStoredPrivatelyAsPendingWithMetadata() throws Exception {
        HospitalDocumentsOverview res = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("registration.pdf"),
                LocalDate.now().minusYears(1), null);

        DocumentView d = res.documents().get(0);
        assertEquals(DocumentStatus.PENDING, d.status());
        assertEquals("registration.pdf", d.fileName());
        assertEquals("application/pdf", d.contentType());
        assertEquals(64, d.sha256().length());
        assertEquals(HOSPITAL_USER, d.uploadedBy());
        assertNotNull(d.uploadedAt());
        HospitalDocument stored = docs.get(d.id());
        assertNotEquals("registration.pdf", stored.getStoredFilename(), "stored name must be server-generated");
        assertTrue(Files.exists(uploadDir.resolve(stored.getStoredFilename())));
        verify(notifier).admins(eq("Hospital document awaiting review"), contains("City General Hospital"));
        verify(auditService).record(eq("HOSPITAL_DOCUMENT_UPLOADED"), eq("HospitalDocument"), eq(d.id()), anyString());
    }

    @Test
    void upload_ValidPngAndJpg_AreAccepted() throws Exception {
        upload(HospitalDocumentType.ACCREDITATION_CERTIFICATE, image("nabh.png", "png"), null, null);
        HospitalDocumentsOverview res = upload(HospitalDocumentType.ACCREDITATION_CERTIFICATE, image("nabl.jpg", "jpg"), null, null);
        // Accreditation allows several current documents side by side.
        assertEquals(2, res.documents().size());
    }

    @Test
    void upload_RejectsWrongExtension() {
        BusinessException ex = assertThrows(BusinessException.class, () -> upload(HospitalDocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "cert.exe", "application/octet-stream", PDF), null, null));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
        assertTrue(docs.isEmpty());
    }

    @Test
    void upload_RejectsContentThatDoesNotMatchExtension() throws Exception {
        byte[] png = image("x.png", "png").getBytes();
        BusinessException ex = assertThrows(BusinessException.class, () -> upload(HospitalDocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "renamed.pdf", "application/pdf", png), null, null));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());

        assertThrows(BusinessException.class, () -> upload(HospitalDocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "fake.png", "image/png", PDF), null, null));
        assertTrue(docs.isEmpty());
        assertEquals(0, Files.list(uploadDir).count(), "nothing may be written for a rejected file");
    }

    @Test
    void upload_RejectsTruncatedPdfAndPdfWithJavaScript() {
        assertThrows(BusinessException.class, () -> upload(HospitalDocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "cut.pdf", "application/pdf", "%PDF-1.4\n1 0 obj".getBytes()), null, null));
        byte[] js = "%PDF-1.4\n<< /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >>\n%%EOF".getBytes();
        BusinessException ex = assertThrows(BusinessException.class, () -> upload(HospitalDocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "js.pdf", "application/pdf", js), null, null));
        assertTrue(ex.getMessage().contains("scripts"));
    }

    @Test
    void upload_RejectsOversizedFile() {
        byte[] big = new byte[(int) (5 * 1024 * 1024) + 10];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        BusinessException ex = assertThrows(BusinessException.class, () -> upload(HospitalDocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "big.pdf", "application/pdf", big), null, null));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
    }

    @Test
    void upload_ValidatesDates() {
        FieldValidationException future = assertThrows(FieldValidationException.class, () -> upload(
                HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("a.pdf"), LocalDate.now().plusDays(1), null));
        assertTrue(future.getFieldErrors().containsKey("issueDate"));

        FieldValidationException expired = assertThrows(FieldValidationException.class, () -> upload(
                HospitalDocumentType.OPERATING_LICENSE, pdf("a.pdf"), LocalDate.now().minusYears(2), LocalDate.now().minusDays(1)));
        assertTrue(expired.getFieldErrors().get("expiryDate").contains("expired"));

        hospital.setOperatesBloodBank(true);
        FieldValidationException noExpiry = assertThrows(FieldValidationException.class, () -> upload(
                HospitalDocumentType.BLOOD_BANK_LICENSE, pdf("bb.pdf"), LocalDate.now().minusYears(1), null));
        assertTrue(noExpiry.getFieldErrors().containsKey("expiryDate"));
    }

    @Test
    void upload_AdditionalRequestedTypeNeedsAnAdminRequest() {
        assertThrows(FieldValidationException.class,
                () -> upload(HospitalDocumentType.ADDITIONAL_REQUESTED, pdf("x.pdf"), null, null));
    }

    // ---------- access ----------

    @Test
    void hospital_CannotDownloadOrRemoveAnotherHospitalsDocument() throws Exception {
        long id = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("mine.pdf"), null, null).documents().get(0).id();

        assertThrows(ResourceNotFoundException.class, () -> documents.myFile(OTHER_HOSPITAL_USER, id));
        assertThrows(ResourceNotFoundException.class, () -> documents.remove(OTHER_HOSPITAL_USER, id));
        assertNotNull(documents.myFile(HOSPITAL_USER, id).path());
        assertNotNull(documents.fileForAdmin(id).path());
    }

    // ---------- review ----------

    @Test
    void approve_RecordsReviewerAndTimestamp_AndRejectRequiresRemarks() throws Exception {
        long id = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("r.pdf"), null, null).documents().get(0).id();

        assertThrows(BusinessException.class, () -> review.reject(id, ADMIN, "   ", false));
        review.approve(id, ADMIN, "Matches state register");

        HospitalDocument d = docs.get(id);
        assertEquals(DocumentStatus.VERIFIED, d.getStatus());
        assertEquals(ADMIN, d.getReviewedBy());
        assertNotNull(d.getReviewedAt());
        assertEquals("Matches state register", d.getReviewRemarks());
        verify(notifier).hospital(eq(hospital), eq("Document approved"), anyString());
    }

    @Test
    void reject_StoresRemarks_AndHospitalSeesActionRequired() throws Exception {
        long id = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("r.pdf"), null, null).documents().get(0).id();

        review.reject(id, ADMIN, "Certificate is unreadable", true);

        HospitalDocument d = docs.get(id);
        assertEquals(DocumentStatus.REJECTED, d.getStatus());
        assertEquals("Certificate is unreadable", d.getReviewRemarks());
        Compliance c = verification.assess(hospital);
        assertEquals(HospitalVerificationStatus.ACTION_REQUIRED, c.status());
        assertEquals(1, requests.size(), "requestCorrection opens a corrected-document request");
        verify(notifier).hospital(eq(hospital), eq("Document rejected"), contains("Certificate is unreadable"));
    }

    @Test
    void fullyVerified_OnlyWhenAdminVerifiedAndAllMandatoryDocumentsApproved() throws Exception {
        hospital.setVerified(true);
        assertFalse(verification.isFullyVerified(hospital), "admin flag alone is not enough");

        approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
        assertFalse(verification.isFullyVerified(hospital));
        approveNew(HospitalDocumentType.OPERATING_LICENSE);
        assertTrue(verification.isFullyVerified(hospital));
        assertEquals(HospitalVerificationStatus.VERIFIED, verification.assess(hospital).status());

        hospital.setVerified(false);
        Compliance c = verification.assess(hospital);
        assertFalse(c.fullyVerified());
        assertTrue(c.documentsComplete());
        assertTrue(c.message().contains("final administrator approval"));
    }

    @Test
    void bloodBankLicense_RequiredOnlyForHospitalsOperatingABloodBank() throws Exception {
        hospital.setVerified(true);
        approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
        approveNew(HospitalDocumentType.OPERATING_LICENSE);
        assertTrue(verification.isFullyVerified(hospital), "requesting blood never requires a bank license");

        hospital.setOperatesBloodBank(true);
        Compliance c = verification.assess(hospital);
        assertFalse(c.fullyVerified());
        assertEquals(List.of("Blood bank license"), c.outstanding());
    }

    @Test
    void categoryRule_OverridesTheGeneralRule() throws Exception {
        rules.add(rule(9L, HospitalDocumentType.OPERATING_LICENSE, HospitalCategory.GOVERNMENT_HOSPITAL,
                RequirementCondition.ALWAYS, false, false));
        hospital.setVerified(true);
        approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
        assertFalse(verification.isFullyVerified(hospital));

        hospital.setRegistrationCategory(HospitalCategory.GOVERNMENT_HOSPITAL);
        assertTrue(verification.isFullyVerified(hospital));
    }

    // ---------- resubmission ----------

    @Test
    void replacingAnApprovedDocument_TriggersReverification_AndKeepsHistory() throws Exception {
        hospital.setVerified(true);
        long oldId = approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
        approveNew(HospitalDocumentType.OPERATING_LICENSE);
        assertTrue(verification.isFullyVerified(hospital));

        HospitalDocumentsOverview res = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("renewed.pdf"), null, null);

        HospitalDocument old = docs.get(oldId);
        assertFalse(old.isCurrent());
        assertEquals(DocumentStatus.VERIFIED, old.getStatus(), "history keeps the original decision");
        assertNotNull(old.getSupersededAt());
        DocumentView replacement = res.documents().stream()
                .filter(d -> d.documentType() == HospitalDocumentType.REGISTRATION_CERTIFICATE).findFirst().orElseThrow();
        assertEquals(DocumentStatus.PENDING, replacement.status());
        assertEquals(replacement.id(), old.getSupersededBy());
        assertEquals(3, res.history().size());
        assertFalse(verification.isFullyVerified(hospital));
        verify(notifier).verificationLost(eq(hospital), any(), contains("re-verification"));

        review.approve(replacement.id(), ADMIN, null);
        assertTrue(verification.isFullyVerified(hospital));
    }

    @Test
    void approve_RefusesASupersededVersion() throws Exception {
        long oldId = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("v1.pdf"), null, null).documents().get(0).id();
        upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("v2.pdf"), null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> review.approve(oldId, ADMIN, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ---------- expiry ----------

    @Test
    void expiredDocument_IsNeverValid_AndJobMarksItAndNotifiesAdmins() throws Exception {
        hospital.setVerified(true);
        approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
        long licenseId = approveNew(HospitalDocumentType.OPERATING_LICENSE);
        assertTrue(verification.isFullyVerified(hospital));

        // Time passes: the approved license's expiry date is now in the past.
        docs.get(licenseId).setExpiryDate(LocalDate.now().minusDays(1));

        Compliance c = verification.assess(hospital);
        assertFalse(c.fullyVerified(), "expiry takes effect live, before the job runs");
        assertEquals(HospitalVerificationStatus.VERIFICATION_EXPIRED, c.status());

        assertEquals(1, expiryJob.markExpired(LocalDate.now()));
        assertEquals(DocumentStatus.EXPIRED, docs.get(licenseId).getStatus());
        verify(notifier).hospital(eq(hospital), eq("Document expired"), anyString());
        verify(notifier).admins(eq("Hospital verification expired"), contains("City General Hospital"));
        assertThrows(BusinessException.class, () -> review.approve(licenseId, ADMIN, null));
    }

    @Test
    void expiryJob_WarnsOnceBeforeExpiry() throws Exception {
        long id = approveNew(HospitalDocumentType.OPERATING_LICENSE);
        docs.get(id).setExpiryDate(LocalDate.now().plusDays(10));

        assertEquals(1, expiryJob.warnExpiringSoon(LocalDate.now()));
        assertEquals(0, expiryJob.warnExpiringSoon(LocalDate.now()));
        verify(notifier, times(1)).hospital(eq(hospital), eq("Document expiring soon"), anyString());
    }

    // ---------- admin requests ----------

    @Test
    void adminRequestedDocument_IsMandatoryUntilApproved() throws Exception {
        hospital.setVerified(true);
        approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
        approveNew(HospitalDocumentType.OPERATING_LICENSE);

        HospitalReview r = review.requestDocument(1L, ADMIN,
                new CreateDocumentRequest(HospitalDocumentType.ADDITIONAL_REQUESTED, "Fire safety certificate", "Needed for audit"));
        long requestId = r.requests().get(0).id();
        assertEquals(HospitalVerificationStatus.ACTION_REQUIRED, verification.assess(hospital).status());
        assertFalse(verification.isFullyVerified(hospital));

        HospitalDocumentsOverview res = documents.upload(HOSPITAL_USER, pdf("fire.pdf"), null, null, null, null, requestId);
        assertEquals(HospitalDocumentRequest.Status.SUBMITTED, requests.get(requestId).getStatus());
        long docId = res.documents().stream().filter(d -> Objects.equals(d.requestId(), requestId)).findFirst().orElseThrow().id();

        review.approve(docId, ADMIN, null);
        assertEquals(HospitalDocumentRequest.Status.FULFILLED, requests.get(requestId).getStatus());
        assertTrue(verification.isFullyVerified(hospital));
    }

    // ---------- legacy transition window ----------

    @Test
    void legacyGrace_KeepsPreviouslyVerifiedHospitalActiveOnlyUntilTheDateAndOnlyWhileNothingIsRejected() throws Exception {
        hospital.setVerified(true);
        HospitalVerificationService withGrace = new HospitalVerificationService(documentRepository, requirementRepository,
                requestRepository, new HospitalDocumentProperties(null, null, LocalDate.now().plusDays(30)));
        HospitalVerificationService graceOver = new HospitalVerificationService(documentRepository, requirementRepository,
                requestRepository, new HospitalDocumentProperties(null, null, LocalDate.now().minusDays(1)));

        assertTrue(withGrace.isFullyVerified(hospital));
        assertTrue(withGrace.assess(hospital).inLegacyGrace());
        assertFalse(graceOver.isFullyVerified(hospital));

        long id = upload(HospitalDocumentType.REGISTRATION_CERTIFICATE, pdf("r.pdf"), null, null).documents().get(0).id();
        assertTrue(withGrace.isFullyVerified(hospital), "still in grace while under review");
        review.reject(id, ADMIN, "Wrong document", false);
        assertFalse(withGrace.isFullyVerified(hospital), "a rejection ends the grace");
    }

    // ---------- backend restriction on request actions ----------

    @Nested
    class RequestAuthorization {

        @Mock private BloodRequestRepository bloodRequestRepository;
        @Mock private PatientRepository patientRepository;
        @Mock private DonorRepository donorRepository;
        @Mock private DonationHistoryRepository donationRepository;
        @Mock private NotificationService notificationService;

        private BloodRequestService requestService;
        private BloodRequest pending;

        @BeforeEach
        void setUp() {
            requestService = new BloodRequestService(bloodRequestRepository, patientRepository, hospitalRepository,
                    donorRepository, donationRepository, mock(FileStorageService.class), notificationService, verification,
                    mock(com.sben.patient.PatientService.class), mock(com.sben.user.UserRepository.class),
                    mock(AuditService.class));
            pending = BloodRequest.builder().id(50L).patientId(7L).bloodGroup("O+").unitsRequired(2)
                    .urgency(Urgency.HIGH).status(RequestStatus.PENDING).build();
            when(bloodRequestRepository.findById(50L)).thenReturn(Optional.of(pending));
            when(bloodRequestRepository.save(any(BloodRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void adminVerifiedHospitalWithoutApprovedDocuments_CannotVerifyOrMoveRequests() {
            hospital.setVerified(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> requestService.verify(50L, HOSPITAL_USER));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
            assertTrue(ex.getMessage().contains("not fully verified"));
            assertThrows(BusinessException.class, () -> requestService.updateStatus(50L, RequestStatus.REJECTED,
                    HOSPITAL_USER, com.sben.user.Role.HOSPITAL));
            assertEquals(RequestStatus.PENDING, pending.getStatus());
        }

        @Test
        void fullyVerifiedHospital_CanVerify() throws Exception {
            hospital.setVerified(true);
            approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
            approveNew(HospitalDocumentType.OPERATING_LICENSE);

            assertEquals(RequestStatus.VERIFIED, requestService.verify(50L, HOSPITAL_USER).status());
            assertEquals(1L, pending.getHospitalId());
        }

        @Test
        void hospitalWhoseDocumentExpired_IsBlockedButAdminCanStillActOnItsRequests() throws Exception {
            hospital.setVerified(true);
            approveNew(HospitalDocumentType.REGISTRATION_CERTIFICATE);
            long license = approveNew(HospitalDocumentType.OPERATING_LICENSE);
            pending.setStatus(RequestStatus.VERIFIED);
            pending.setHospitalId(1L);
            docs.get(license).setExpiryDate(LocalDate.now().minusDays(1));

            BusinessException ex = assertThrows(BusinessException.class, () -> requestService.updateStatus(50L,
                    RequestStatus.REJECTED, HOSPITAL_USER, com.sben.user.Role.HOSPITAL));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
            assertEquals(RequestStatus.VERIFIED, pending.getStatus(), "existing request keeps its state and owner");
            assertEquals(1L, pending.getHospitalId());
            // An admin can still move the paused request on.
            assertEquals(RequestStatus.REJECTED, requestService.updateStatus(50L, RequestStatus.REJECTED,
                    ADMIN, com.sben.user.Role.ADMIN).status());
        }
    }

    // ---------- helpers ----------

    private HospitalDocumentsOverview upload(HospitalDocumentType type, MockMultipartFile file, LocalDate issue, LocalDate expiry) {
        return documents.upload(HOSPITAL_USER, file, type, issue, expiry, null, null);
    }

    /** Upload a document of this type for the main hospital and approve it as an admin. */
    private long approveNew(HospitalDocumentType type) {
        HospitalDocumentsOverview res = upload(type, pdf(type.name().toLowerCase() + ".pdf"), LocalDate.now().minusYears(1),
                type == HospitalDocumentType.BLOOD_BANK_LICENSE ? LocalDate.now().plusYears(1) : null);
        long id = res.documents().stream().filter(d -> d.documentType() == type && d.status() == DocumentStatus.PENDING)
                .findFirst().orElseThrow().id();
        review.approve(id, ADMIN, null);
        return id;
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private static MockMultipartFile image(String name, String format) throws Exception {
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return new MockMultipartFile("file", name, "image/" + ("jpg".equals(format) ? "jpeg" : format), out.toByteArray());
    }

    private static Hospital completeHospital(long id, long userId, String name) {
        return Hospital.builder().id(id).userId(userId).hospitalName(name).licenseNumber("HOSP-" + id)
                .registrationCategory(HospitalCategory.PRIVATE_HOSPITAL).phone("9876543230")
                .address("45 Medical Enclave").city("Mumbai").state("Maharashtra").pincode("400001")
                .verified(false).build();
    }

    private static HospitalDocumentRequirement rule(long id, HospitalDocumentType type, HospitalCategory category,
                                                    RequirementCondition when, boolean mandatory, boolean expiryRequired) {
        return HospitalDocumentRequirement.builder().id(id).documentType(type).registrationCategory(category)
                .appliesWhen(when).mandatory(mandatory).expiryDateRequired(expiryRequired).active(true)
                .sortOrder((int) id * 10).build();
    }

    private void stubDocumentStore() {
        when(documentRepository.save(any(HospitalDocument.class))).thenAnswer(inv -> {
            HospitalDocument d = inv.getArgument(0);
            if (d.getId() == null) d.setId(ids.incrementAndGet());
            docs.put(d.getId(), d);
            return d;
        });
        when(documentRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(docs.get(inv.<Long>getArgument(0))));
        when(documentRepository.findByHospitalIdAndCurrentTrueAndDeletedFalseOrderByUploadedAtDesc(anyLong()))
                .thenAnswer(inv -> newestFirst(d -> d.getHospitalId().equals(inv.getArgument(0)) && d.isCurrent()));
        when(documentRepository.findByHospitalIdAndDeletedFalseOrderByUploadedAtDesc(anyLong()))
                .thenAnswer(inv -> newestFirst(d -> d.getHospitalId().equals(inv.getArgument(0))));
        when(documentRepository.findByHospitalIdAndDocumentTypeAndCurrentTrueAndDeletedFalse(anyLong(), any()))
                .thenAnswer(inv -> newestFirst(d -> d.getHospitalId().equals(inv.getArgument(0))
                        && d.getDocumentType() == inv.getArgument(1) && d.isCurrent()));
        when(documentRepository.findByDocumentRequestIdAndCurrentTrueAndDeletedFalse(anyLong()))
                .thenAnswer(inv -> newestFirst(d -> inv.getArgument(0).equals(d.getDocumentRequestId()) && d.isCurrent()));
        when(documentRepository.findNewlyExpired(anyCollection(), any())).thenAnswer(inv -> {
            Collection<DocumentStatus> statuses = inv.getArgument(0);
            LocalDate today = inv.getArgument(1);
            return newestFirst(d -> d.isCurrent() && statuses.contains(d.getStatus()) && d.isExpired(today));
        });
        when(documentRepository.findExpiringSoon(any(), any())).thenAnswer(inv -> {
            LocalDate today = inv.getArgument(0);
            LocalDate until = inv.getArgument(1);
            return newestFirst(d -> d.isCurrent() && d.getStatus() == DocumentStatus.VERIFIED && d.getExpiryDate() != null
                    && !d.getExpiryDate().isBefore(today) && !d.getExpiryDate().isAfter(until) && d.getExpiryWarningSentAt() == null);
        });
    }

    private void stubRequestStore() {
        when(requestRepository.save(any(HospitalDocumentRequest.class))).thenAnswer(inv -> {
            HospitalDocumentRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(ids.incrementAndGet());
            requests.put(r.getId(), r);
            return r;
        });
        when(requestRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<Long>getArgument(0))));
        when(requestRepository.findByHospitalIdAndStatusInAndDeletedFalseOrderByRequestedAtAsc(anyLong(), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<HospitalDocumentRequest.Status> statuses = inv.getArgument(1);
                    return requests.values().stream().filter(r -> r.getHospitalId().equals(inv.getArgument(0))
                            && statuses.contains(r.getStatus())).toList();
                });
        when(requestRepository.findByHospitalIdAndDeletedFalseOrderByRequestedAtDesc(anyLong()))
                .thenAnswer(inv -> requests.values().stream().filter(r -> r.getHospitalId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(HospitalDocumentRequest::getId).reversed()).toList());
    }

    private List<HospitalDocument> newestFirst(java.util.function.Predicate<HospitalDocument> p) {
        return docs.values().stream().filter(p).sorted(Comparator.comparing(HospitalDocument::getId).reversed()).toList();
    }
}
