package com.sben.hospital.dto;

import com.sben.hospital.Hospital;

import java.math.BigDecimal;

public record HospitalResponse(
        Long id, Long userId, String hospitalName, String licenseNumber, String phone,
        String address, String city, String state, String pincode,
        BigDecimal latitude, BigDecimal longitude, boolean verified
) {
    public static HospitalResponse from(Hospital h) {
        return new HospitalResponse(h.getId(), h.getUserId(), h.getHospitalName(),
                h.getLicenseNumber(), h.getPhone(), h.getAddress(), h.getCity(), h.getState(),
                h.getPincode(), h.getLatitude(), h.getLongitude(), h.isVerified());
    }
}
