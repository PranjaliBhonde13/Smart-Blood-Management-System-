package com.sben.admin;

import com.sben.admin.dto.DashboardStats;

import com.sben.audit.AuditService;
import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.bloodrequest.BloodRequestRepository;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.donation.DonationHistoryRepository;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.*;
import com.sben.hospital.dto.HospitalResponse;
import com.sben.notification.NotificationService;
import com.sben.user.Role;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sben.bloodbank.LicenseStatus;
import com.sben.common.exception.BusinessException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFunctionalityTest {

    @Mock private UserRepository userRepository;
    @Mock private HospitalRepository hospitalRepository;
    @Mock private BloodBankRepository bloodBankRepository;
    @Mock private BloodRequestRepository requestRepository;
    @Mock private DonationHistoryRepository donationRepository;
    @Mock private BloodStockRepository stockRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private com.sben.notification.email.AccountEmails accountEmails;
    @Mock private HospitalDocumentRepository documentRepository;
    @Mock private HospitalDocumentRequirementRepository requirementRepository;
    @Mock private HospitalDocumentRequestRepository documentRequestRepository;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(
                userRepository,
                hospitalRepository,
                bloodBankRepository,
                requestRepository,
                donationRepository,
                stockRepository,
                auditService,
                notificationService,
                new HospitalVerificationService(documentRepository, requirementRepository,
                        documentRequestRepository, HospitalDocumentProperties.defaults()),
                documentRepository,
                accountEmails
        );
    }

    @Test
    void dashboard_ShouldReturnStats() {
        when(userRepository.countByDeletedFalse()).thenReturn(5L);
        when(userRepository.countByRoleAndDeletedFalse(Role.PATIENT)).thenReturn(1L);
        when(userRepository.countByRoleAndDeletedFalse(Role.DONOR)).thenReturn(1L);
        when(userRepository.countByRoleAndDeletedFalse(Role.HOSPITAL)).thenReturn(1L);
        when(userRepository.countByRoleAndDeletedFalse(Role.BLOOD_BANK)).thenReturn(1L);

        DashboardStats stats = service.dashboard();
        assertNotNull(stats);
        assertEquals(5L, stats.totalUsers());
        assertEquals(1L, stats.patients());
        assertEquals(1L, stats.donors());
        assertEquals(1L, stats.hospitals());
        assertEquals(1L, stats.bloodBanks());
    }

    @Test
    void verifyHospital_ShouldSetVerifiedTrueAndNotify() {
        Hospital hospital = Hospital.builder().id(10L).userId(20L).hospitalName("City Hosp").verified(false).build();
        when(hospitalRepository.findById(10L)).thenReturn(Optional.of(hospital));

        HospitalResponse res = service.verifyHospital(10L);
        assertTrue(res.verified());
        verify(hospitalRepository).save(hospital);
        verify(auditService).record(eq("HOSPITAL_VERIFIED"), eq("Hospital"), eq(10L), eq("City Hosp"));
        verify(notificationService).notify(eq(20L), anyString(), anyString(), any());
    }

    @Test
    void verifyHospital_ShouldBeRefusedUntilMandatoryDocumentsAreApproved() {
        Hospital hospital = Hospital.builder().id(10L).userId(20L).hospitalName("City Hosp").verified(false).build();
        when(hospitalRepository.findById(10L)).thenReturn(Optional.of(hospital));
        when(requirementRepository.findByActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                HospitalDocumentRequirement.builder().id(1L).documentType(HospitalDocumentType.REGISTRATION_CERTIFICATE)
                        .appliesWhen(RequirementCondition.ALWAYS).mandatory(true).active(true).build()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.verifyHospital(10L, 1L));
        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertTrue(ex.getMessage().contains("Hospital registration certificate"));
        assertFalse(hospital.isVerified());
        verify(hospitalRepository, never()).save(any());
    }

    @Test
    void verifyBloodBank_ShouldSetVerifiedTrueAndNotify() {
        BloodBank bank = BloodBank.builder().id(15L).userId(25L).bankName("Red Cross").verified(false)
                .licenseDocument("abc.pdf").build();
        when(bloodBankRepository.findById(15L)).thenReturn(Optional.of(bank));

        BloodBankResponse res = service.verifyBloodBank(15L, 1L);
        assertTrue(res.verified());
        assertEquals(LicenseStatus.VERIFIED, res.verificationStatus());
        assertEquals(1L, bank.getVerifiedBy());
        assertNotNull(bank.getVerifiedAt());
        verify(bloodBankRepository).save(bank);
        verify(auditService).record(eq("BLOOD_BANK_VERIFIED"), eq("BloodBank"), eq(15L), eq("Red Cross"));
        verify(notificationService).notify(eq(25L), anyString(), anyString(), any());
    }

    @Test
    void verifyBloodBank_ShouldRefuseWithoutLicenseDocument() {
        BloodBank bank = BloodBank.builder().id(15L).userId(25L).bankName("Red Cross").verified(false).build();
        when(bloodBankRepository.findById(15L)).thenReturn(Optional.of(bank));

        assertThrows(BusinessException.class, () -> service.verifyBloodBank(15L, 1L));
        assertFalse(bank.isVerified());
        verify(bloodBankRepository, never()).save(any());
    }

    @Test
    void verifyBloodBank_ShouldRefuseExpiredLicense() {
        BloodBank bank = BloodBank.builder().id(15L).userId(25L).bankName("Red Cross").verified(false)
                .licenseDocument("abc.pdf").licenseExpiryDate(LocalDate.now().minusDays(1)).build();
        when(bloodBankRepository.findById(15L)).thenReturn(Optional.of(bank));

        assertThrows(BusinessException.class, () -> service.verifyBloodBank(15L, 1L));
        assertFalse(bank.isVerified());
    }

    @Test
    void rejectBloodBank_ShouldRequireReasonAndStoreIt() {
        BloodBank bank = BloodBank.builder().id(15L).userId(25L).bankName("Red Cross").verified(true)
                .licenseDocument("abc.pdf").build();
        when(bloodBankRepository.findById(15L)).thenReturn(Optional.of(bank));

        assertThrows(BusinessException.class, () -> service.rejectBloodBank(15L, "  "));
        BloodBankResponse res = service.rejectBloodBank(15L, "License number not found in registry");
        assertEquals(LicenseStatus.REJECTED, res.verificationStatus());
        assertEquals("License number not found in registry", res.rejectionReason());
        assertFalse(res.verified());
    }
}
