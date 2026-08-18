package com.sben.bloodstock.dto;

import com.sben.bloodstock.BloodStock;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BloodStockResponse(
        Long id, Long bloodBankId, String bloodGroup, Integer unitsAvailable,
        LocalDate expiryDate, LocalDateTime lastUpdated
) {
    public static BloodStockResponse from(BloodStock s) {
        return new BloodStockResponse(s.getId(), s.getBloodBankId(), s.getBloodGroup(),
                s.getUnitsAvailable(), s.getExpiryDate(), s.getLastUpdated());
    }
}
