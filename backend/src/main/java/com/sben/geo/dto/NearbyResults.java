package com.sben.geo.dto;

import java.math.BigDecimal;

/**
 * Result rows for nearby search. Contact details are intentionally omitted for
 * donors (privacy) until a request is accepted (Module 5); banks/hospitals are
 * public-facing so their contact is included.
 */
public final class NearbyResults {

    public record NearbyDonor(
            Long donorId, String bloodGroup, String city,
            BigDecimal latitude, BigDecimal longitude, double distanceKm
    ) {}

    public record NearbyBloodBank(
            Long bloodBankId, String bankName, String city, String phone,
            int compatibleUnits, BigDecimal latitude, BigDecimal longitude,
            double distanceKm, boolean verified
    ) {}

    public record NearbyHospital(
            Long hospitalId, String hospitalName, String city, String phone,
            BigDecimal latitude, BigDecimal longitude, double distanceKm
    ) {}

    private NearbyResults() {}
}
