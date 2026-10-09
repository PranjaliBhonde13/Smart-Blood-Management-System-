package com.sben.geo;

import com.sben.common.exception.BusinessException;
import com.sben.common.response.ApiResponse;
import com.sben.geo.dto.NearbyResults.NearbyBloodBank;
import com.sben.geo.dto.NearbyResults.NearbyDonor;
import com.sben.geo.dto.NearbyResults.NearbyHospital;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Geo/matching endpoints (see docs/04-API.md §6). Radius is capped server-side
 * to keep queries bounded.
 */
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Tag(name = "Geo / Matching")
public class GeoController {

    private static final double MAX_RADIUS_KM = 100.0;

    private final GeoService geoService;

    @GetMapping("/nearby/donors")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ApiResponse<List<NearbyDonor>> nearbyDonors(@RequestParam String bloodGroup,
                                                       @RequestParam double lat,
                                                       @RequestParam double lng,
                                                       @RequestParam(defaultValue = "10") double radiusKm) {
        validate(lat, lng);
        return ApiResponse.ok(geoService.nearbyDonors(normalise(bloodGroup), lat, lng, cap(radiusKm)));
    }

    @GetMapping("/nearby/blood-banks")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ApiResponse<List<NearbyBloodBank>> nearbyBanks(@RequestParam String bloodGroup,
                                                          @RequestParam double lat,
                                                          @RequestParam double lng,
                                                          @RequestParam(defaultValue = "15") double radiusKm) {
        validate(lat, lng);
        return ApiResponse.ok(geoService.nearbyBloodBanks(normalise(bloodGroup), lat, lng, cap(radiusKm)));
    }

    @GetMapping("/nearby/hospitals")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ApiResponse<List<NearbyHospital>> nearbyHospitals(@RequestParam double lat,
                                                             @RequestParam double lng,
                                                             @RequestParam(defaultValue = "15") double radiusKm) {
        validate(lat, lng);
        return ApiResponse.ok(geoService.nearbyHospitals(lat, lng, cap(radiusKm)));
    }

    /** An unencoded '+' in a query string arrives as a space ("A " means "A+"). */
    private static String normalise(String bloodGroup) {
        return bloodGroup.trim().length() < bloodGroup.length() && !bloodGroup.contains("+")
                ? bloodGroup.trim() + "+" : bloodGroup.trim();
    }

    private static void validate(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException("lat must be between -90 and 90 and lng between -180 and 180",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private double cap(double radiusKm) {
        return Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM);
    }
}
