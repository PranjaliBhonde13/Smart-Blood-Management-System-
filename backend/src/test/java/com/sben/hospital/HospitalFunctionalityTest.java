package com.sben.hospital;

import com.sben.common.exception.BusinessException;
import com.sben.hospital.document.*;
import com.sben.hospital.dto.HospitalRequest;
import com.sben.hospital.dto.HospitalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HospitalFunctionalityTest {

    @Mock private HospitalRepository repository;
    @Mock private HospitalDocumentRepository documentRepository;
    @Mock private HospitalDocumentRequirementRepository requirementRepository;
    @Mock private HospitalDocumentRequestRepository requestRepository;
    @Mock private HospitalDocumentNotifier notifier;

    private HospitalService service;

    @BeforeEach
    void setUp() {
        HospitalVerificationService verification = new HospitalVerificationService(
                documentRepository, requirementRepository, requestRepository, HospitalDocumentProperties.defaults());
        service = new HospitalService(repository, verification, notifier);
        when(repository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getMyProfile_ShouldReturnHospitalProfile() {
        Hospital hospital = Hospital.builder()
                .id(1L)
                .userId(102L)
                .hospitalName("City General Hospital")
                .licenseNumber("HOSP-100200")
                .phone("9876543230")
                .city("Mumbai")
                .verified(true)
                .build();

        when(repository.findByUserId(102L)).thenReturn(Optional.of(hospital));

        HospitalResponse res = service.getMyProfile(102L);
        assertNotNull(res);
        assertEquals("City General Hospital", res.hospitalName());
        assertEquals("HOSP-100200", res.licenseNumber());
        assertTrue(res.verified());
    }

    @Test
    void upsertMyProfile_ShouldRejectDuplicateLicenseOnNewRegistration() {
        HospitalRequest req = new HospitalRequest(
                "City Hospital", "HOSP-100200", "9876543230", "45 Med Enclave", "Mumbai", "MH", "400001",
                new BigDecimal("19.0800"), new BigDecimal("72.8800")
        );

        when(repository.findByUserId(102L)).thenReturn(Optional.empty());
        when(repository.existsByLicenseNumber("HOSP-100200")).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.upsertMyProfile(102L, req));
    }

    @Test
    void upsertMyProfile_TurningOnOwnBloodBank_AddsBloodBankLicenseRequirementAndAlertsAdmins() {
        // Verified hospital with no mandatory rules except the conditional blood bank license.
        Hospital hospital = Hospital.builder().id(1L).userId(102L).hospitalName("City Hospital")
                .licenseNumber("HOSP-1").registrationCategory(HospitalCategory.PRIVATE_HOSPITAL)
                .phone("9876543230").address("45 Med Enclave").city("Mumbai").state("MH").pincode("400001")
                .verified(true).build();
        when(repository.findByUserId(102L)).thenReturn(Optional.of(hospital));
        when(requirementRepository.findByActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                HospitalDocumentRequirement.builder().id(3L).documentType(HospitalDocumentType.BLOOD_BANK_LICENSE)
                        .appliesWhen(RequirementCondition.OPERATES_BLOOD_BANK).mandatory(true).active(true).build()));
        assertTrue(service.getMyProfile(102L).fullyVerified(), "a hospital that only requests blood needs no bank license");

        HospitalResponse res = service.upsertMyProfile(102L, new HospitalRequest("City Hospital", "HOSP-1",
                "9876543230", "45 Med Enclave", "Mumbai", "MH", "400001", null, null,
                HospitalCategory.PRIVATE_HOSPITAL, true));

        assertTrue(res.operatesBloodBank());
        assertFalse(res.fullyVerified());
        assertEquals(HospitalVerificationStatus.DOCUMENTS_PENDING, res.verificationStatus());
        verify(notifier).verificationLost(eq(hospital), any(), eq("profile details changed"));
    }
}
