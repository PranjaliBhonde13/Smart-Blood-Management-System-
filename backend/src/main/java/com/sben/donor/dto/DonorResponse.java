package com.sben.donor.dto;

import com.sben.donor.Availability;
import com.sben.donor.Donor;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DonorResponse(
        Long id, Long userId, String bloodGroup, Integer age, String city,
        BigDecimal latitude, BigDecimal longitude, LocalDate lastDonationDate,
        Availability availability
) {
    public static DonorResponse from(Donor d) {
        return new DonorResponse(d.getId(), d.getUserId(), d.getBloodGroup(), d.getAge(),
                d.getCity(), d.getLatitude(), d.getLongitude(), d.getLastDonationDate(),
                d.getAvailability());
    }
}
