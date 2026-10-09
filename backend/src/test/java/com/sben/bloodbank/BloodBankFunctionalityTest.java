package com.sben.bloodbank;

import com.sben.audit.AuditService;
import com.sben.bloodbank.dto.BloodBankRequest;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.common.exception.BusinessException;
import com.sben.common.storage.FileStorageService;
import com.sben.notification.NotificationService;
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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloodBankFunctionalityTest {

    private static final byte[] PDF = "%PDF-1.4 test".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Mock private BloodBankRepository repository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;
    @TempDir Path uploadDir;

    private FileStorageService storage;
    private BloodBankService service;

    @BeforeEach
    void setUp() throws Exception {
        storage = new FileStorageService(uploadDir.toString());
        var init = FileStorageService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(storage);
        service = new BloodBankService(repository, storage, auditService, notificationService, userRepository);
        when(repository.save(any(BloodBank.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByRoleAndDeletedFalse(any(), any())).thenReturn(Page.empty());
    }

    private static BloodBankRequest request(String name, String license, LocalDate issue, LocalDate expiry) {
        return new BloodBankRequest(name, "Ops@RedCross.org", "9876543240", "", "https://redcross.org",
                "88 Lifesaver Ave", "Maharashtra", "Mumbai", "400001",
                new BigDecimal("19.0850"), new BigDecimal("72.8850"), license, issue, expiry);
    }

    private static BloodBank verifiedBank() {
        return BloodBank.builder().id(1L).userId(103L).bankName("Red Cross").licenseNumber("BANK-300400")
                .licenseDocument("old.pdf").verified(true).verifiedAt(LocalDateTime.now()).verifiedBy(1L).build();
    }

    // ---------- profile ----------

    @Test
    void getMyProfile_ShouldReturnBloodBankProfile() {
        BloodBank bank = verifiedBank();
        when(repository.findByUserId(103L)).thenReturn(Optional.of(bank));

        BloodBankResponse res = service.getMyProfile(103L);
        assertEquals("Red Cross", res.bankName());
        assertTrue(res.verified());
        assertEquals(LicenseStatus.VERIFIED, res.verificationStatus());
        assertTrue(res.hasLicenseDocument());
    }

    @Test
    void upsertMyProfile_ShouldRejectDuplicateLicenseOnNewRegistration() {
        when(repository.findByUserId(103L)).thenReturn(Optional.empty());
        when(repository.existsByLicenseNumber("BANK-300400")).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.upsertMyProfile(103L, request("Red Cross", "BANK-300400", null, null)));
    }

    @Test
    void upsertMyProfile_NewBankStartsUnverifiedAndNotSubmitted() {
        when(repository.findByUserId(103L)).thenReturn(Optional.empty());

        BloodBankResponse res = service.upsertMyProfile(103L, request("Red Cross", "BANK-1", null, null));
        assertFalse(res.verified());
        assertEquals(LicenseStatus.NOT_SUBMITTED, res.verificationStatus());
        assertEquals("ops@redcross.org", res.contactEmail());
        assertNull(res.alternatePhone(), "blank optional fields are stored as null");
    }

    @Test
    void upsertMyProfile_ShouldRejectExpiryBeforeIssue() {
        LocalDate issue = LocalDate.now().minusYears(1);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.upsertMyProfile(103L,
                request("Red Cross", "BANK-1", issue, issue.minusDays(1))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void upsertMyProfile_ChangingLicenseNumberTriggersReverification() {
        BloodBank bank = verifiedBank();
        when(repository.findByUserId(103L)).thenReturn(Optional.of(bank));

        BloodBankResponse res = service.upsertMyProfile(103L, request("Red Cross", "BANK-999", null, null));
        assertFalse(res.verified());
        assertEquals(LicenseStatus.PENDING, res.verificationStatus());
        assertNull(bank.getVerifiedBy());
        verify(auditService).record(eq("BLOOD_BANK_LICENSE_RESUBMITTED"), any(), any(), any());
    }

    @Test
    void upsertMyProfile_ContactChangesKeepVerification() {
        BloodBank bank = verifiedBank();
        when(repository.findByUserId(103L)).thenReturn(Optional.of(bank));

        BloodBankResponse res = service.upsertMyProfile(103L, request("Red Cross", "BANK-300400", null, null));
        assertTrue(res.verified());
        assertEquals(LicenseStatus.VERIFIED, res.verificationStatus());
    }

    @Test
    void upsertMyProfile_SaveAfterRejectionResubmits() {
        BloodBank bank = verifiedBank();
        bank.setVerified(false);
        bank.setRejectedAt(LocalDateTime.now());
        bank.setRejectionReason("Unreadable scan");
        when(repository.findByUserId(103L)).thenReturn(Optional.of(bank));

        BloodBankResponse res = service.upsertMyProfile(103L, request("Red Cross", "BANK-300400", null, null));
        assertEquals(LicenseStatus.PENDING, res.verificationStatus());
        assertNull(res.rejectionReason());
    }

    // ---------- license document ----------

    @Test
    void uploadLicense_ReplacingDocumentOfVerifiedBankTriggersReverification() {
        BloodBank bank = verifiedBank();
        when(repository.findByUserId(103L)).thenReturn(Optional.of(bank));

        BloodBankResponse res = service.uploadLicenseDocument(103L,
                new MockMultipartFile("file", "../../etc/My \"License\".pdf", "application/pdf", PDF));

        assertFalse(res.verified());
        assertEquals(LicenseStatus.PENDING, res.verificationStatus());
        assertEquals("application/pdf", res.licenseDocumentType());
        assertEquals("My _License_.pdf", res.licenseDocumentName(), "path and quotes stripped from display name");
        assertNotEquals("old.pdf", bank.getLicenseDocument());
        assertTrue(Files.exists(uploadDir.resolve(bank.getLicenseDocument())), "stored under a random name");
        verify(auditService).record(eq("BLOOD_BANK_LICENSE_REPLACED"), any(), any(), any());
    }

    @Test
    void uploadLicense_RejectsDisallowedTypeAndSpoofedContent() {
        when(repository.findByUserId(103L)).thenReturn(Optional.of(verifiedBank()));

        assertThrows(BusinessException.class, () -> service.uploadLicenseDocument(103L,
                new MockMultipartFile("file", "license.docx", "application/msword", PDF)));
        assertThrows(BusinessException.class, () -> service.uploadLicenseDocument(103L,
                new MockMultipartFile("file", "license.pdf", "application/pdf", "not a pdf".getBytes())));
    }

    @Test
    void uploadLicense_RejectsFilesOver5Mb() {
        when(repository.findByUserId(103L)).thenReturn(Optional.of(verifiedBank()));
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadLicenseDocument(103L,
                new MockMultipartFile("file", "license.pdf", "application/pdf", big)));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
    }

    @Test
    void uploadLogo_AcceptsImagesOnlyAndDoesNotTouchVerification() {
        BloodBank bank = verifiedBank();
        when(repository.findByUserId(103L)).thenReturn(Optional.of(bank));

        assertThrows(BusinessException.class, () -> service.uploadLogo(103L,
                new MockMultipartFile("file", "logo.pdf", "application/pdf", PDF)));
        BloodBankResponse res = service.uploadLogo(103L,
                new MockMultipartFile("file", "logo.png", "image/png", PNG));
        assertTrue(res.hasLogo());
        assertTrue(res.verified());
    }

    // ---------- status ----------

    @Test
    void expiredLicense_IsNeverActiveEvenIfVerified() {
        BloodBank bank = verifiedBank();
        bank.setLicenseExpiryDate(LocalDate.now().minusDays(1));

        assertEquals(LicenseStatus.EXPIRED, bank.getVerificationStatus());
        assertFalse(bank.isActiveOnNetwork());

        bank.setLicenseExpiryDate(LocalDate.now());
        assertEquals(LicenseStatus.VERIFIED, bank.getVerificationStatus(), "valid through its expiry day");
        assertTrue(bank.isActiveOnNetwork());
    }
}
