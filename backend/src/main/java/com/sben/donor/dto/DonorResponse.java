package com.sben.donor.dto;

import com.sben.donor.Availability;
import com.sben.donor.ContactMethod;
import com.sben.donor.Donor;
import com.sben.donor.HealthScreening;
import com.sben.donor.ScreeningAnswer;
import com.sben.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The caller's own donor profile, joined with the account fields the profile
 * page edits (name, phone) or displays (email and its verification). Health
 * screening answers appear here only, never in admin or matching views.
 */
public record DonorResponse(
        Long id, Long userId,
        String fullName, String email, boolean emailVerified, String phone,
        String bloodGroup, LocalDate dateOfBirth, Integer age, String gender,
        String address, String city, String state, String pincode,
        BigDecimal latitude, BigDecimal longitude,
        Boolean hasDonatedBefore, LocalDate lastDonationDate,
        Availability availability, ContactMethod preferredContactMethod,
        Boolean contactConsent, LocalDateTime contactConsentUpdatedAt,
        String alternatePhone, String emergencyContactName, String emergencyContactPhone,
        boolean hasPhoto,
        HealthScreeningResponse healthScreening
) {
    public record HealthScreeningResponse(
            ScreeningAnswer feelingWell, ScreeningAnswer recentIllness, ScreeningAnswer currentMedication,
            ScreeningAnswer recentProcedure, ScreeningAnswer chronicCondition, ScreeningAnswer pregnancy,
            LocalDateTime screenedAt
    ) {
        static HealthScreeningResponse from(HealthScreening h) {
            if (h == null || h.getScreenedAt() == null) return null;
            return new HealthScreeningResponse(h.getFeelingWell(), h.getRecentIllness(), h.getCurrentMedication(),
                    h.getRecentProcedure(), h.getChronicCondition(), h.getPregnancy(), h.getScreenedAt());
        }
    }

    /** {@code user} may be null (e.g. in tests that only stub the donor row). */
    public static DonorResponse from(Donor d, User user) {
        return new DonorResponse(d.getId(), d.getUserId(),
                user != null ? user.getFullName() : null,
                user != null ? user.getEmail() : null,
                user != null && user.isEmailVerified(),
                user != null ? user.getPhone() : null,
                d.getBloodGroup(), d.getDateOfBirth(), d.currentAge(), d.getGender(),
                d.getAddress(), d.getCity(), d.getState(), d.getPincode(),
                d.getLatitude(), d.getLongitude(),
                d.getHasDonatedBefore(), d.getLastDonationDate(),
                d.getAvailability(), d.getPreferredContactMethod(),
                d.getContactConsent(), d.getContactConsentUpdatedAt(),
                d.getAlternatePhone(), d.getEmergencyContactName(), d.getEmergencyContactPhone(),
                d.getPhotoFile() != null,
                HealthScreeningResponse.from(d.getHealthScreening()));
    }
}
