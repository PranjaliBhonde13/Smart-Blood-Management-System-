package com.sben.bloodbank.dto;

import com.sben.bloodbank.BloodBank;

import java.math.BigDecimal;

public record BloodBankResponse(
        Long id, Long userId, String bankName, String licenseNumber, String phone,
        String address, String city, BigDecimal latitude, BigDecimal longitude, boolean verified
) {
    public static BloodBankResponse from(BloodBank b) {
        return new BloodBankResponse(b.getId(), b.getUserId(), b.getBankName(),
                b.getLicenseNumber(), b.getPhone(), b.getAddress(), b.getCity(),
                b.getLatitude(), b.getLongitude(), b.isVerified());
    }
}
