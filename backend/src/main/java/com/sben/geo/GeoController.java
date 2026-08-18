package com.sben.geo;

import com.sben.common.response.ApiResponse;
import com.sben.geo.dto.NearbyResults.NearbyBloodBank;
import com.sben.geo.dto.NearbyResults.NearbyDonor;
import com.sben.geo.dto.NearbyResults.NearbyHospital;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
        return ApiResponse.ok(geoService.nearbyDonors(bloodGroup, lat, lng, cap(radiusKm)));
    }

    @GetMapping("/nearby/blood-banks")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ApiResponse<List<NearbyBloodBank>> nearbyBanks(@RequestParam String bloodGroup,
                                                          @RequestParam double lat,
                                                          @RequestParam double lng,
                                                          @RequestParam(defaultValue = "15") double radiusKm) {
        return ApiResponse.ok(geoService.nearbyBloodBanks(bloodGroup, lat, lng, cap(radiusKm)));
    }

    @GetMapping("/nearby/hospitals")
    @PreAuthorize("hasAnyRole('PATIENT','HOSPITAL','ADMIN')")
    public ApiResponse<List<NearbyHospital>> nearbyHospitals(@RequestParam double lat,
                                                             @RequestParam double lng,
                                                             @RequestParam(defaultValue = "15") double radiusKm) {
        return ApiResponse.ok(geoService.nearbyHospitals(lat, lng, cap(radiusKm)));
    }

    private double cap(double radiusKm) {
        return Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM);
    }
}
