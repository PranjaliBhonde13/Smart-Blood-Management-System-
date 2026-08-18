package com.sben.donation.dto;

import com.sben.donation.DonationHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DonationResponse(
        Long id, Long requestId, Long donorId, Long bloodBankId, Long hospitalId,
        Integer unitsDonated, LocalDate donationDate, DonationHistory.Status status,
        LocalDateTime createdAt
) {
    public static DonationResponse from(DonationHistory d) {
        return new DonationResponse(d.getId(), d.getRequestId(), d.getDonorId(), d.getBloodBankId(),
                d.getHospitalId(), d.getUnitsDonated(), d.getDonationDate(), d.getStatus(), d.getCreatedAt());
    }
}
