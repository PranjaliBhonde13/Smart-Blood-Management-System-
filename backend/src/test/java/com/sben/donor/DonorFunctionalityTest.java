package com.sben.donor;

import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.donation.DonationHistory;
import com.sben.donation.DonationHistoryRepository;
import com.sben.donor.dto.DonorRequest;
import com.sben.donor.dto.DonorRequest.HealthScreeningRequest;
import com.sben.donor.dto.DonorResponse;
import com.sben.user.Role;
import com.sben.user.User;
import com.sben.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DonorFunctionalityTest {

    @Mock private DonorRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private DonationHistoryRepository donationRepository;
    @Mock private FileStorageService fileStorageService;

    private DonorService service;
    private User user;

    private static final LocalDate DOB = LocalDate.now().minusYears(30);
    private static final HealthScreeningRequest ALL_NO = new HealthScreeningRequest(
            ScreeningAnswer.YES, ScreeningAnswer.NO, ScreeningAnswer.NO, ScreeningAnswer.NO,
            ScreeningAnswer.NO, ScreeningAnswer.NOT_APPLICABLE, true);

    @BeforeEach
    void setUp() {
        service = new DonorService(repository, userRepository, donationRepository, fileStorageService);
        user = User.builder().id(101L).fullName("Old Name").email("donor@sben.com")
                .phone("9876543220").role(Role.DONOR).emailVerified(true).build();
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(repository.save(any(Donor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(donationRepository.findFirstByDonorIdAndStatusOrderByDonationDateDesc(anyLong(), any()))
                .thenReturn(Optional.empty());
    }

    /** A request varying only the fields the tests below care about. */
    private static DonorRequest request(LocalDate dob, Boolean donatedBefore, LocalDate lastDonation,
                                        BigDecimal lat, BigDecimal lng, Boolean consent,
                                        HealthScreeningRequest screening) {
        return new DonorRequest("  New Name ", dob, "B+", "+91 98765-43210", null,
                "12 MG Road", "Pune", "Maharashtra", "411001", lat, lng,
                donatedBefore, lastDonation, Availability.AVAILABLE, ContactMethod.SMS, consent,
                null, null, null, screening);
    }

    private static DonorRequest validRequest() {
        return request(DOB, true, LocalDate.now().minusMonths(4),
                new BigDecimal("18.5204"), new BigDecimal("73.8567"), true, ALL_NO);
    }

    private Donor existingDonor() {
        Donor donor = Donor.builder().id(1L).userId(101L).bloodGroup("A+").age(28)
                .availability(Availability.AVAILABLE).build();
        when(repository.findByUserId(101L)).thenReturn(Optional.of(donor));
        return donor;
    }

    @Test
    void getMyProfile_ShouldReturnProfileWithAccountFields() {
        Donor donor = existingDonor();
        donor.setCity("Mumbai");

        DonorResponse res = service.getMyProfile(101L);
        assertEquals("A+", res.bloodGroup());
        assertEquals("Mumbai", res.city());
        assertEquals("donor@sben.com", res.email());
        assertTrue(res.emailVerified());
        assertEquals(28, res.age(), "legacy age is shown until a date of birth is saved");
        assertNull(res.healthScreening());
    }

    @Test
    void upsert_ShouldSaveAllSectionsAndAccountFields() {
        Donor donor = existingDonor();

        DonorResponse res = service.upsertMyProfile(101L, validRequest());

        assertEquals("New Name", user.getFullName());
        assertEquals("+919876543210", user.getPhone());
        assertEquals("B+", res.bloodGroup());
        assertEquals(30, res.age(), "age is derived from the date of birth");
        assertNull(donor.getAge(), "stored legacy age is cleared");
        assertEquals("Maharashtra", res.state());
        assertEquals("411001", res.pincode());
        assertEquals(new BigDecimal("18.5204000"), res.latitude());
        assertTrue(res.hasDonatedBefore());
        assertEquals(ContactMethod.SMS, res.preferredContactMethod());
        assertTrue(res.contactConsent());
        assertNotNull(res.contactConsentUpdatedAt());
        assertNotNull(res.healthScreening());
        assertNotNull(res.healthScreening().screenedAt());
    }

    @Test
    void upsert_ShouldCreateProfileForFirstTimeDonor() {
        when(repository.findByUserId(101L)).thenReturn(Optional.empty());

        DonorResponse res = service.upsertMyProfile(101L, validRequest());
        assertEquals(101L, res.userId());
        verify(repository).save(any(Donor.class));
    }

    @Test
    void upsert_ShouldRejectDonorUnder18() {
        existingDonor();
        DonorRequest req = request(LocalDate.now().minusYears(17), false, null, null, null, true, null);

        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(101L, req));
        assertTrue(ex.getFieldErrors().containsKey("dateOfBirth"));
        verify(repository, never()).save(any());
        assertEquals("Old Name", user.getFullName(), "nothing is written when validation fails");
    }

    @Test
    void upsert_ShouldAllowManualAddressWithoutCoordinates() {
        existingDonor();
        DonorResponse res = service.upsertMyProfile(101L, request(DOB, false, null, null, null, true, null));
        assertNull(res.latitude());
        assertNull(res.longitude());
    }

    @Test
    void upsert_ShouldRequireCoordinatesAsAPair() {
        existingDonor();
        DonorRequest req = request(DOB, false, null, new BigDecimal("18.5"), null, true, null);

        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(101L, req));
        assertTrue(ex.getFieldErrors().containsKey("longitude"));
    }

    @Test
    void upsert_ShouldRequireLastDonationDateWhenDonatedBefore() {
        existingDonor();
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(101L, request(DOB, true, null, null, null, true, null)));
        assertTrue(ex.getFieldErrors().containsKey("lastDonationDate"));
    }

    @Test
    void upsert_ShouldRejectLastDonationDateWhenNeverDonated() {
        existingDonor();
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(101L,
                        request(DOB, false, LocalDate.now().minusDays(10), null, null, true, null)));
        assertTrue(ex.getFieldErrors().containsKey("lastDonationDate"));
    }

    @Test
    void upsert_ShouldNotAllowBackdatingBeforeRecordedDonation() {
        existingDonor();
        LocalDate recorded = LocalDate.now().minusDays(20);
        DonationHistory completed = DonationHistory.builder().donorId(1L).donationDate(recorded)
                .status(DonationHistory.Status.COMPLETED).build();
        when(donationRepository.findFirstByDonorIdAndStatusOrderByDonationDateDesc(1L, DonationHistory.Status.COMPLETED))
                .thenReturn(Optional.of(completed));

        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(101L,
                        request(DOB, true, LocalDate.now().minusDays(200), null, null, true, null)));
        assertTrue(ex.getFieldErrors().get("lastDonationDate").contains(recorded.toString()));
    }

    @Test
    void upsert_ShouldRecordConsentOptOut() {
        Donor donor = existingDonor();
        donor.setContactConsent(true);

        DonorResponse res = service.upsertMyProfile(101L, request(DOB, false, null, null, null, false, null));
        assertFalse(res.contactConsent());
        assertNotNull(res.contactConsentUpdatedAt(), "consent changes are timestamped");
    }

    @Test
    void upsert_ShouldRequireEveryScreeningAnswerAndDeclaration() {
        existingDonor();
        HealthScreeningRequest partial = new HealthScreeningRequest(ScreeningAnswer.YES, null,
                ScreeningAnswer.NOT_APPLICABLE, ScreeningAnswer.NO, ScreeningAnswer.NO, ScreeningAnswer.NO, false);

        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> service.upsertMyProfile(101L, request(DOB, false, null, null, null, true, partial)));
        assertTrue(ex.getFieldErrors().containsKey("healthScreening.recentIllness"));
        assertTrue(ex.getFieldErrors().containsKey("healthScreening.currentMedication"),
                "not applicable is only valid for the pregnancy question");
        assertTrue(ex.getFieldErrors().containsKey("healthScreening.declarationAccepted"));
    }

    @Test
    void upsert_ShouldKeepScreeningTimestampWhenAnswersUnchanged_AndClearOnNull() {
        Donor donor = existingDonor();
        LocalDateTime earlier = LocalDateTime.now().minusDays(5);
        donor.setHealthScreening(HealthScreening.builder().feelingWell(ScreeningAnswer.YES)
                .recentIllness(ScreeningAnswer.NO).currentMedication(ScreeningAnswer.NO)
                .recentProcedure(ScreeningAnswer.NO).chronicCondition(ScreeningAnswer.NO)
                .pregnancy(ScreeningAnswer.NOT_APPLICABLE).screenedAt(earlier).build());

        DonorResponse res = service.upsertMyProfile(101L, validRequest());
        assertEquals(earlier, res.healthScreening().screenedAt());

        res = service.upsertMyProfile(101L, request(DOB, false, null, null, null, true, null));
        assertNull(res.healthScreening(), "answers can be withdrawn");
    }

    @Test
    void normalisePhone_ShouldStoreE164StyleIndianNumber() {
        assertEquals("+919876543210", DonorService.normalisePhone("09876543210"));
        assertEquals("+919876543210", DonorService.normalisePhone("+91 98765 43210"));
        assertNull(DonorService.normalisePhone("  "));
    }

    @Test
    void setAvailability_ShouldToggleAvailability() {
        existingDonor();
        DonorResponse res = service.setAvailability(101L, Availability.UNAVAILABLE);
        assertEquals(Availability.UNAVAILABLE, res.availability());
    }

    @Test
    void setAvailability_ShouldThrowException_WhenProfileNotFound() {
        when(repository.findByUserId(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.setAvailability(999L, Availability.UNAVAILABLE));
    }
}
