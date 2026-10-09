package com.sben.donor;

import com.sben.donor.dto.DonorRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Bean validation on the profile payload: formats, ranges and mandatory fields. */
class DonorRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static DonorRequest req(String phone, String pincode, BigDecimal lat, BigDecimal lng,
                                    LocalDate dob, Availability availability, Boolean consent, String city) {
        return new DonorRequest("Asha Rao", dob, "O+", phone, null, null, city, "Karnataka", pincode,
                lat, lng, null, null, availability, null, consent, null, null, null, null);
    }

    private static Set<String> invalidFields(DonorRequest r) {
        return validator.validate(r).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static final LocalDate DOB = LocalDate.of(1995, 5, 17);

    @Test
    void validRequest_HasNoViolations() {
        assertTrue(invalidFields(req("9876543210", "560001", new BigDecimal("12.97"), new BigDecimal("77.59"),
                DOB, Availability.AVAILABLE, true, "Bengaluru")).isEmpty());
    }

    @Test
    void phone_AcceptsCommonIndianFormats() {
        for (String ok : new String[]{"9876543210", "+919876543210", "+91 98765 43210", "09876543210", "98765-43210"}) {
            assertFalse(invalidFields(req(ok, "560001", null, null, DOB, Availability.AVAILABLE, true, "B"))
                    .contains("phone"), ok);
        }
        for (String bad : new String[]{"12345", "5876543210", "98765432101", "+1 415 555 0100", "abcdefghij"}) {
            assertTrue(invalidFields(req(bad, "560001", null, null, DOB, Availability.AVAILABLE, true, "B"))
                    .contains("phone"), bad);
        }
    }

    @Test
    void pincode_MustBeSixDigitsNotStartingWithZero() {
        assertTrue(invalidFields(req("9876543210", "056001", null, null, DOB, Availability.AVAILABLE, true, "B")).contains("pincode"));
        assertTrue(invalidFields(req("9876543210", "56001", null, null, DOB, Availability.AVAILABLE, true, "B")).contains("pincode"));
        assertTrue(invalidFields(req("9876543210", "5600A1", null, null, DOB, Availability.AVAILABLE, true, "B")).contains("pincode"));
    }

    @Test
    void coordinates_MustBeInRange() {
        Set<String> bad = invalidFields(req("9876543210", "560001", new BigDecimal("91"), new BigDecimal("-181"),
                DOB, Availability.AVAILABLE, true, "B"));
        assertTrue(bad.contains("latitude"));
        assertTrue(bad.contains("longitude"));
    }

    @Test
    void mandatoryFields_AreEnforced() {
        Set<String> bad = invalidFields(req(null, null, null, null, null, null, null, " "));
        assertTrue(bad.containsAll(Set.of("phone", "pincode", "dateOfBirth", "availability", "contactConsent", "city")), bad.toString());
    }

    @Test
    void dateOfBirth_MustBeInThePast() {
        assertTrue(invalidFields(req("9876543210", "560001", null, null, LocalDate.now().plusDays(1),
                Availability.AVAILABLE, true, "B")).contains("dateOfBirth"));
    }
}
