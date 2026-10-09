package com.sben.hospital;

import com.sben.common.exception.FieldValidationException;
import com.sben.hospital.document.*;
import com.sben.hospital.dto.HospitalRequest;
import com.sben.hospital.dto.HospitalResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Hospital profile: required fields, contacts, phone formats and coordinates. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HospitalProfileDetailsTest {

    @Mock private HospitalRepository repository;
    @Mock private HospitalDocumentRepository documentRepository;
    @Mock private HospitalDocumentRequirementRepository requirementRepository;
    @Mock private HospitalDocumentRequestRepository requestRepository;
    @Mock private HospitalDocumentNotifier notifier;

    private HospitalService service;
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        HospitalVerificationService verification = new HospitalVerificationService(
                documentRepository, requirementRepository, requestRepository, HospitalDocumentProperties.defaults());
        service = new HospitalService(repository, verification, notifier);
        when(repository.save(any(Hospital.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByUserId(102L)).thenReturn(Optional.of(Hospital.builder().id(1L).userId(102L)
                .hospitalName("City Hospital").licenseNumber("HOSP-1").build()));
    }

    private static HospitalRequest request(String phone, String emergencyPhone, BigDecimal lat, BigDecimal lng) {
        return new HospitalRequest("City Hospital", "HOSP-1", phone, "45 Med Enclave", "Mumbai", "Maharashtra",
                "400001", lat, lng, HospitalCategory.PRIVATE_HOSPITAL, false,
                emergencyPhone, "blood@cityhospital.in", "Dr. Meera Iyer", "Blood transfusion officer");
    }

    private static Set<String> invalid(HospitalRequest r) {
        return VALIDATOR.validate(r).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void savesContactsAndNormalisesNumbers() {
        HospitalResponse res = service.upsertMyProfile(102L,
                request("022 2654 3210", "+91 98765-43210", new BigDecimal("19.08"), new BigDecimal("72.88")));
        assertEquals("+912226543210", res.phone());
        assertEquals("+919876543210", res.emergencyPhone());
        assertEquals("blood@cityhospital.in", res.officialEmail());
        assertEquals("Dr. Meera Iyer", res.contactPersonName());
        assertEquals("Blood transfusion officer", res.contactPersonDesignation());
        assertEquals(new BigDecimal("19.0800000"), res.latitude());
        assertEquals(HospitalCategory.PRIVATE_HOSPITAL, res.registrationCategory());
    }

    @Test
    void coordinatesMustComeAsAPair() {
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(102L, request("9876543230", null, new BigDecimal("19.08"), null)));
        assertTrue(ex.getFieldErrors().containsKey("longitude"));
        verify(repository, never()).save(any());
    }

    @Test
    void coordinatesAreOptional() {
        HospitalResponse res = service.upsertMyProfile(102L, request("9876543230", null, null, null));
        assertNull(res.latitude());
        assertNull(res.emergencyPhone());
    }

    @Test
    void legacyPhoneValuesAreKeptAsEntered() {
        assertEquals("12345", HospitalService.normalisePhone("12345"));
        assertNull(HospitalService.normalisePhone(" "));
    }

    @Test
    void validRequestPassesBeanValidation() {
        assertTrue(invalid(request("+91 22 26543210", "9876543210", null, null)).isEmpty());
    }

    @Test
    void phoneFormats() {
        for (String ok : new String[]{"9876543210", "+91 98765 43210", "022 2654 3210", "+91 22 26543210", "080-2345-6789"}) {
            assertFalse(invalid(request(ok, null, null, null)).contains("phone"), ok);
        }
        for (String bad : new String[]{"12345", "0123456789", "+1 415 555 0100", "98765432101"}) {
            assertTrue(invalid(request(bad, null, null, null)).contains("phone"), bad);
        }
        assertTrue(invalid(request("9876543210", "123", null, null)).contains("emergencyPhone"));
    }

    @Test
    void requiredFieldsAndFormats() {
        HospitalRequest blank = new HospitalRequest("", "", "", "", "", "", "", null, null, null, null,
                null, "not-an-email", null, null);
        Set<String> bad = invalid(blank);
        assertTrue(bad.containsAll(Set.of("hospitalName", "licenseNumber", "phone", "address", "city", "state",
                "pincode", "registrationCategory", "operatesBloodBank", "officialEmail")), bad.toString());
        assertTrue(invalid(new HospitalRequest("H", "L", "9876543210", "A", "C", "S", "040001",
                new BigDecimal("91"), new BigDecimal("0"), HospitalCategory.CLINIC, false))
                .containsAll(Set.of("pincode", "latitude")));
    }
}
