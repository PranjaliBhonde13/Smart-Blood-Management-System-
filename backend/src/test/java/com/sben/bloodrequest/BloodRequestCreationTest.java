package com.sben.bloodrequest;

import com.sben.audit.AuditService;
import com.sben.bloodrequest.dto.BloodRequestResponse;
import com.sben.bloodrequest.dto.ConfirmBloodGroupRequest;
import com.sben.bloodrequest.dto.CreateBloodRequestRequest;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.FieldValidationException;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalVerificationService;
import com.sben.notification.NotificationService;
import com.sben.patient.Patient;
import com.sben.patient.PatientRepository;
import com.sben.patient.PatientService;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Request creation for self/other, server-side blood-group sourcing, confirmation gate and redaction. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloodRequestCreationTest {

    @Mock private BloodRequestRepository repository;
    @Mock private PatientRepository patientRepository;
    @Mock private HospitalRepository hospitalRepository;
    @Mock private com.sben.donor.DonorRepository donorRepository;
    @Mock private com.sben.donation.DonationHistoryRepository donationRepository;
    @Mock private com.sben.common.storage.FileStorageService fileStorageService;
    @Mock private NotificationService notificationService;
    @Mock private HospitalVerificationService verificationService;
    @Mock private PatientService patientService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private BloodRequestService service;

    private final Patient patient = Patient.builder().id(1L).userId(100L).bloodGroup("A+")
            .dateOfBirth(LocalDate.now().minusYears(30)).build();

    @BeforeEach
    void setUp() {
        when(patientRepository.findByUserId(100L)).thenReturn(Optional.of(patient));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(userRepository.findById(100L)).thenReturn(Optional.of(User.builder().id(100L).fullName("Asha Rao").build()));
        when(repository.save(any(BloodRequest.class))).thenAnswer(i -> {
            BloodRequest r = i.getArgument(0);
            if (r.getId() == null) r.setId(55L);
            return r;
        });
    }

    private static CreateBloodRequestRequest req(RequestFor forWhom, String group, String name, Integer age) {
        return new CreateBloodRequestRequest(forWhom, group, BloodComponent.PACKED_RED_CELLS, 2, Urgency.HIGH,
                LocalDateTime.now().plusHours(6), name, age, null, null, null, null, null);
    }

    private void profileGroup(String group, boolean verified) {
        when(patientService.profileBloodGroup(any())).thenReturn(new PatientService.ProfileBloodGroup(group, verified));
    }

    @Test
    void self_UsesVerifiedProfileGroupEvenWhenClientSendsNone() {
        profileGroup("A+", true);
        BloodRequestResponse res = service.create(100L, req(RequestFor.SELF, null, null, null));
        assertEquals("A+", res.bloodGroup());
        assertEquals(BloodGroupSource.PROFILE_VERIFIED, res.bloodGroupSource());
        assertEquals("Asha Rao", res.recipientName());
        assertEquals(30, res.recipientAge());
        assertTrue(res.bloodGroupApprovable());
    }

    @Test
    void self_RejectsAClientGroupThatDiffersFromTheProfile() {
        profileGroup("A+", true);
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.create(100L, req(RequestFor.SELF, "O-", null, null)));
        assertTrue(ex.getFieldErrors().containsKey("bloodGroup"));
        verify(repository, never()).save(any());
    }

    @Test
    void self_WithoutProfileGroupIsRefused() {
        profileGroup(null, false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(100L, req(RequestFor.SELF, null, null, null)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void self_UnverifiedGroup_IsSubmittedButCannotBeApprovedUntilConfirmed() {
        profileGroup("A+", false);
        BloodRequestResponse res = service.create(100L, req(RequestFor.SELF, null, null, null));
        assertEquals(BloodGroupSource.PROFILE_UNVERIFIED, res.bloodGroupSource());
        assertFalse(res.bloodGroupApprovable());

        BloodRequest stored = captureSaved();
        stored.setStatus(RequestStatus.VERIFIED);
        when(repository.findById(55L)).thenReturn(Optional.of(stored));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateStatus(55L, RequestStatus.APPROVED, 1L, Role.ADMIN));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());

        service.confirmBloodGroup(55L, new ConfirmBloodGroupRequest("A+", null), 1L, Role.ADMIN);
        verify(auditService).record(eq("BLOOD_REQUEST_GROUP_CONFIRMED"), eq("BloodRequest"), eq(55L), anyString());
        assertTrue(stored.isBloodGroupApprovable());
        assertEquals(RequestStatus.APPROVED, service.updateStatus(55L, RequestStatus.APPROVED, 1L, Role.ADMIN).status());
    }

    @Test
    void other_UsesDeclaredGroupNeverTheRequestersOwn() {
        profileGroup("A+", true);
        BloodRequestResponse res = service.create(100L, req(RequestFor.OTHER, "B-", "Ravi Rao", 62));
        assertEquals("B-", res.bloodGroup());
        assertEquals(BloodGroupSource.DECLARED, res.bloodGroupSource());
        assertEquals("Ravi Rao", res.recipientName());
        assertFalse(res.bloodGroupApprovable(), "declared groups need confirmation");
        verify(patientService, never()).profileBloodGroup(any());
    }

    @Test
    void other_RequiresGroupNameAndAge() {
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.create(100L, req(RequestFor.OTHER, null, " ", null)));
        assertTrue(ex.getFieldErrors().keySet().containsAll(java.util.Set.of("bloodGroup", "recipientName", "recipientAge")));
    }

    @Test
    void requiredByInThePastIsRejected() {
        profileGroup("A+", true);
        CreateBloodRequestRequest past = new CreateBloodRequestRequest(RequestFor.SELF, null, BloodComponent.PLATELETS, 1,
                Urgency.CRITICAL, LocalDateTime.now().minusHours(2), null, null, null, null, null, null, null);
        FieldValidationException ex = assertThrows(FieldValidationException.class, () -> service.create(100L, past));
        assertTrue(ex.getFieldErrors().containsKey("requiredBy"));
    }

    @Test
    void preferredHospitalMustBeFullyVerified() {
        profileGroup("A+", true);
        Hospital h = Hospital.builder().id(7L).userId(70L).hospitalName("City Care").build();
        when(hospitalRepository.findById(7L)).thenReturn(Optional.of(h));
        when(verificationService.isFullyVerified(h)).thenReturn(false);
        CreateBloodRequestRequest withHospital = new CreateBloodRequestRequest(RequestFor.SELF, null,
                BloodComponent.WHOLE_BLOOD, 1, Urgency.MEDIUM, LocalDateTime.now().plusDays(1),
                null, null, null, 7L, null, null, null);
        assertThrows(FieldValidationException.class, () -> service.create(100L, withHospital));

        when(verificationService.isFullyVerified(h)).thenReturn(true);
        BloodRequestResponse res = service.create(100L, withHospital);
        assertEquals("City Care", res.preferredHospitalName());
        verify(notificationService).notify(eq(70L), anyString(), anyString(), any());
    }

    @Test
    void correction_RequiresReason_IsAudited_AndOnlyBeforeApproval() {
        profileGroup("A+", true);
        service.create(100L, req(RequestFor.OTHER, "B+", "Ravi", 60));
        BloodRequest stored = captureSaved();
        when(repository.findById(55L)).thenReturn(Optional.of(stored));

        assertThrows(FieldValidationException.class,
                () -> service.confirmBloodGroup(55L, new ConfirmBloodGroupRequest("O+", ""), 1L, Role.ADMIN));
        BloodRequestResponse res = service.confirmBloodGroup(55L,
                new ConfirmBloodGroupRequest("O+", "Lab typing at admission shows O+"), 1L, Role.ADMIN);
        assertEquals("O+", res.bloodGroup());
        assertTrue(res.bloodGroupConfirmed());
        verify(auditService).record(eq("BLOOD_REQUEST_GROUP_CORRECTED"), eq("BloodRequest"), eq(55L),
                contains("B+ → O+"));

        stored.setStatus(RequestStatus.APPROVED);
        assertThrows(BusinessException.class,
                () -> service.confirmBloodGroup(55L, new ConfirmBloodGroupRequest("O+", null), 1L, Role.ADMIN));
        assertThrows(BusinessException.class,
                () -> service.confirmBloodGroup(55L, new ConfirmBloodGroupRequest("O+", null), 1L, Role.DONOR));
    }

    @Test
    void redactedViewHidesRecipientIdentity() {
        BloodRequest r = BloodRequest.builder().id(9L).patientId(1L).recipientName("Ravi Rao").recipientAge(62)
                .recipientRelationship("Father").locationNotes("Ward 4").bloodGroup("B+").unitsRequired(1)
                .urgency(Urgency.HIGH).status(RequestStatus.APPROVED).medicalReport("abc.pdf").build();
        BloodRequestResponse v = BloodRequestResponse.redacted(r);
        assertNull(v.recipientName());
        assertNull(v.recipientAge());
        assertNull(v.recipientRelationship());
        assertNull(v.locationNotes());
        assertNull(v.patientId());
        assertNull(v.medicalReport());
        assertEquals("B+", v.bloodGroup());
    }

    private BloodRequest captureSaved() {
        ArgumentCaptor<BloodRequest> c = ArgumentCaptor.forClass(BloodRequest.class);
        verify(repository, atLeastOnce()).save(c.capture());
        return c.getValue();
    }
}
