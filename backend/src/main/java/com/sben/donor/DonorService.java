package com.sben.donor;

import com.sben.bloodbank.BloodBankService;
import com.sben.common.exception.FieldValidationException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.common.storage.FileStorageService;
import com.sben.donation.DonationHistory;
import com.sben.donation.DonationHistoryRepository;
import com.sben.donor.dto.DonorRequest;
import com.sben.donor.dto.DonorRequest.HealthScreeningRequest;
import com.sben.donor.dto.DonorResponse;
import com.sben.user.User;
import com.sben.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the caller's own donor profile plus the quick availability toggle
 * used from the donor dashboard.
 *
 * <p>The profile records what the donor tells us. It never decides medical
 * eligibility: health screening answers are stored for the donor's reference
 * and for staff, who decide eligibility under the applicable guidelines.
 */
@Service
@RequiredArgsConstructor
public class DonorService {

    static final int MIN_DONOR_AGE = 18;
    static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    static final Set<String> PHOTO_TYPES = Set.of("png", "jpg", "jpeg");
    static final long PHOTO_MAX_BYTES = 2L * 1024 * 1024; // 2 MB

    private final DonorRepository repository;
    private final UserRepository userRepository;
    private final DonationHistoryRepository donationRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public DonorResponse getMyProfile(Long userId) {
        Donor donor = requireMine(userId);
        return DonorResponse.from(donor, userRepository.findById(userId).orElse(null));
    }

    @Transactional
    public DonorResponse upsertMyProfile(Long userId, DonorRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Donor donor = repository.findByUserId(userId)
                .orElseGet(() -> Donor.builder().userId(userId).availability(Availability.AVAILABLE).build());

        Map<String, String> errors = validate(donor, req);
        if (!errors.isEmpty()) throw new FieldValidationException(errors);

        // Account fields shown on the profile. Email is the login identity and is not editable here.
        user.setFullName(req.fullName().trim());
        user.setPhone(normalisePhone(req.phone()));
        userRepository.save(user);

        donor.setBloodGroup(req.bloodGroup());
        donor.setDateOfBirth(req.dateOfBirth());
        donor.setAge(null); // age now comes from the date of birth
        donor.setGender(req.gender());

        donor.setAddress(blankToNull(req.address()));
        donor.setCity(req.city().trim());
        donor.setState(req.state().trim());
        donor.setPincode(req.pincode());
        donor.setLatitude(coordinate(req.latitude()));
        donor.setLongitude(coordinate(req.longitude()));

        donor.setLastDonationDate(req.lastDonationDate());
        donor.setHasDonatedBefore(req.lastDonationDate() != null ? Boolean.TRUE : req.hasDonatedBefore());

        donor.setAvailability(req.availability());
        donor.setPreferredContactMethod(req.preferredContactMethod());
        if (!Objects.equals(donor.getContactConsent(), req.contactConsent())) {
            donor.setContactConsentUpdatedAt(now());
        }
        donor.setContactConsent(req.contactConsent());

        donor.setAlternatePhone(normalisePhone(req.alternatePhone()));
        donor.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        donor.setEmergencyContactPhone(normalisePhone(req.emergencyContactPhone()));

        donor.setHealthScreening(screening(donor.getHealthScreening(), req.healthScreening()));

        return DonorResponse.from(repository.save(donor), user);
    }

    @Transactional
    public DonorResponse setAvailability(Long userId, Availability availability) {
        Donor donor = requireMine(userId);
        donor.setAvailability(availability);
        return DonorResponse.from(repository.save(donor), userRepository.findById(userId).orElse(null));
    }

    // ---------- optional profile photo ----------

    @Transactional
    public DonorResponse uploadPhoto(Long userId, MultipartFile file) {
        Donor donor = requireMine(userId);
        String stored = fileStorageService.store(file, PHOTO_TYPES, PHOTO_MAX_BYTES);
        String previous = donor.getPhotoFile();
        donor.setPhotoFile(stored);
        Donor saved = repository.save(donor);
        fileStorageService.delete(previous);
        return DonorResponse.from(saved, userRepository.findById(userId).orElse(null));
    }

    @Transactional
    public DonorResponse removePhoto(Long userId) {
        Donor donor = requireMine(userId);
        String previous = donor.getPhotoFile();
        donor.setPhotoFile(null);
        Donor saved = repository.save(donor);
        fileStorageService.delete(previous);
        return DonorResponse.from(saved, userRepository.findById(userId).orElse(null));
    }

    @Transactional(readOnly = true)
    public BloodBankService.StoredFile myPhoto(Long userId) {
        Donor donor = requireMine(userId);
        if (donor.getPhotoFile() == null) throw new ResourceNotFoundException("No profile photo uploaded");
        String type = donor.getPhotoFile().endsWith(".png") ? "image/png" : "image/jpeg";
        return new BloodBankService.StoredFile(fileStorageService.load(donor.getPhotoFile()), type, "photo");
    }

    // ---------- validation ----------

    /** Rules that depend on more than one field or on stored data. Keys match request field names. */
    private Map<String, String> validate(Donor donor, DonorRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        LocalDate dob = req.dateOfBirth();
        if (dob.isBefore(EARLIEST_BIRTH_DATE)) {
            errors.put("dateOfBirth", "enter a valid date of birth");
        } else if (Period.between(dob, today).getYears() < MIN_DONOR_AGE) {
            errors.put("dateOfBirth", "you must be at least " + MIN_DONOR_AGE + " to register as a donor");
        }

        if ((req.latitude() == null) != (req.longitude() == null)) {
            errors.put(req.latitude() == null ? "latitude" : "longitude",
                    "enter both latitude and longitude, or leave both empty");
        }

        LocalDate last = req.lastDonationDate();
        if (Boolean.FALSE.equals(req.hasDonatedBefore()) && last != null) {
            errors.put("lastDonationDate", "remove the last donation date, or answer that you have donated before");
        } else if (Boolean.TRUE.equals(req.hasDonatedBefore()) && last == null) {
            errors.put("lastDonationDate", "enter your last donation date (an approximate date is fine)");
        } else if (last != null && !last.isAfter(dob)) {
            errors.put("lastDonationDate", "last donation date must be after your date of birth");
        }

        // A donation completed through the network is authoritative: the profile may not contradict it.
        if (donor.getId() != null) {
            donationRepository.findFirstByDonorIdAndStatusOrderByDonationDateDesc(donor.getId(), DonationHistory.Status.COMPLETED)
                    .map(DonationHistory::getDonationDate)
                    .filter(Objects::nonNull)
                    .ifPresent(recorded -> {
                        if (Boolean.FALSE.equals(req.hasDonatedBefore())) {
                            errors.put("hasDonatedBefore", "our records show a completed donation on " + recorded);
                        } else if (last == null || last.isBefore(recorded)) {
                            errors.putIfAbsent("lastDonationDate",
                                    "our records show a completed donation on " + recorded + "; use that date or a later one");
                        }
                    });
        }

        HealthScreeningRequest hs = req.healthScreening();
        if (hs != null) {
            requireAnswer(errors, "feelingWell", hs.feelingWell(), false);
            requireAnswer(errors, "recentIllness", hs.recentIllness(), false);
            requireAnswer(errors, "currentMedication", hs.currentMedication(), false);
            requireAnswer(errors, "recentProcedure", hs.recentProcedure(), false);
            requireAnswer(errors, "chronicCondition", hs.chronicCondition(), false);
            requireAnswer(errors, "pregnancy", hs.pregnancy(), true);
            if (!Boolean.TRUE.equals(hs.declarationAccepted())) {
                errors.put("healthScreening.declarationAccepted", "please confirm your answers are accurate");
            }
        }
        return errors;
    }

    private static void requireAnswer(Map<String, String> errors, String field, ScreeningAnswer answer,
                                      boolean allowNotApplicable) {
        if (answer == null) {
            errors.put("healthScreening." + field, "please answer this question");
        } else if (answer == ScreeningAnswer.NOT_APPLICABLE && !allowNotApplicable) {
            errors.put("healthScreening." + field, "answer yes or no");
        }
    }

    // ---------- helpers ----------

    /** New screening answers; the timestamp moves only when an answer actually changes. */
    private static HealthScreening screening(HealthScreening current, HealthScreeningRequest req) {
        if (req == null) return null;
        HealthScreening next = HealthScreening.builder()
                .feelingWell(req.feelingWell())
                .recentIllness(req.recentIllness())
                .currentMedication(req.currentMedication())
                .recentProcedure(req.recentProcedure())
                .chronicCondition(req.chronicCondition())
                .pregnancy(req.pregnancy())
                .build();
        boolean unchanged = current != null && current.getScreenedAt() != null
                && current.getFeelingWell() == next.getFeelingWell()
                && current.getRecentIllness() == next.getRecentIllness()
                && current.getCurrentMedication() == next.getCurrentMedication()
                && current.getRecentProcedure() == next.getRecentProcedure()
                && current.getChronicCondition() == next.getChronicCondition()
                && current.getPregnancy() == next.getPregnancy();
        next.setScreenedAt(unchanged ? current.getScreenedAt() : now());
        return next;
    }

    /** Stores Indian mobile numbers as +91 followed by the 10-digit number. */
    static String normalisePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("\\D", "");
        return "+91" + digits.substring(digits.length() - 10);
    }

    /** Whole seconds, matching the DATETIME columns, so a saved value reads back unchanged. */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static BigDecimal coordinate(BigDecimal value) {
        return value == null ? null : value.setScale(7, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Donor requireMine(Long userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found. Complete your profile."));
    }
}
