package com.sben.geo;

import com.sben.bloodbank.BloodBank;
import com.sben.bloodbank.BloodBankRepository;
import com.sben.bloodstock.BloodStock;
import com.sben.bloodstock.BloodStockRepository;
import com.sben.common.util.BloodCompatibility;
import com.sben.common.util.GeoUtils;
import com.sben.common.util.GeoUtils.BoundingBox;
import com.sben.donor.Donor;
import com.sben.donor.DonorRepository;
import com.sben.geo.dto.NearbyResults.NearbyBloodBank;
import com.sben.geo.dto.NearbyResults.NearbyDonor;
import com.sben.geo.dto.NearbyResults.NearbyHospital;
import com.sben.hospital.Hospital;
import com.sben.hospital.HospitalRepository;
import com.sben.hospital.document.HospitalVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The matching engine. Combines blood-group compatibility with geospatial radius
 * search: an indexed bounding-box prefilter narrows candidates, then exact
 * Haversine distance filters to the radius and orders results nearest-first.
 */
@Service
@RequiredArgsConstructor
public class GeoService {

    private final DonorRepository donorRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodStockRepository bloodStockRepository;
    private final HospitalRepository hospitalRepository;
    private final HospitalVerificationService verificationService;

    /** Available, compatible donors within {@code radiusKm} of the point. */
    @Transactional(readOnly = true)
    public List<NearbyDonor> nearbyDonors(String recipientGroup, double lat, double lon, double radiusKm) {
        List<String> compatible = BloodCompatibility.donorsFor(recipientGroup);
        BoundingBox box = GeoUtils.boundingBox(lat, lon, radiusKm);
        return donorRepository
                .findAvailableInBox(compatible, box.minLat(), box.maxLat(), box.minLon(), box.maxLon())
                .stream()
                .filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                // Privacy: only an approximate (~1 km) position leaves the server.
                .map(d -> new NearbyDonor(d.getId(), d.getBloodGroup(), d.getCity(),
                        approximate(d.getLatitude()), approximate(d.getLongitude()),
                        round(GeoUtils.haversineKm(d.getLatitude(), d.getLongitude(), lat, lon))))
                .filter(d -> d.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyDonor::distanceKm))
                .toList();
    }

    /** Blood banks holding compatible stock within the radius (units aggregated). */
    @Transactional(readOnly = true)
    public List<NearbyBloodBank> nearbyBloodBanks(String recipientGroup, double lat, double lon, double radiusKm) {
        List<String> compatible = BloodCompatibility.donorsFor(recipientGroup);
        // Sum available units per bank across all compatible groups.
        Map<Long, Integer> unitsByBank = bloodStockRepository
                .findByBloodGroupInAndUnitsAvailableGreaterThanAndDeletedFalse(compatible, 0)
                .stream()
                .filter(s -> s.getExpiryDate() == null || !s.getExpiryDate().isBefore(LocalDate.now()))
                .collect(Collectors.groupingBy(BloodStock::getBloodBankId,
                        Collectors.summingInt(BloodStock::getUnitsAvailable)));
        if (unitsByBank.isEmpty()) return List.of();

        return bloodBankRepository.findByIdInAndDeletedFalse(unitsByBank.keySet())
                .stream()
                .filter(BloodBank::isActiveOnNetwork) // only verified banks with an unexpired license
                .filter(b -> b.getLatitude() != null && b.getLongitude() != null)
                .map(b -> new NearbyBloodBank(b.getId(), b.getBankName(), b.getCity(), b.getPhone(),
                        unitsByBank.getOrDefault(b.getId(), 0),
                        b.getLatitude(), b.getLongitude(),
                        round(GeoUtils.haversineKm(b.getLatitude(), b.getLongitude(), lat, lon)),
                        b.isActiveOnNetwork()))
                .filter(b -> b.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyBloodBank::distanceKm))
                .toList();
    }

    /** Fully verified hospitals (approved, with valid mandatory documents) within the radius. */
    @Transactional(readOnly = true)
    public List<NearbyHospital> nearbyHospitals(double lat, double lon, double radiusKm) {
        BoundingBox box = GeoUtils.boundingBox(lat, lon, radiusKm);
        return hospitalRepository
                .findVerifiedInBox(box.minLat(), box.maxLat(), box.minLon(), box.maxLon())
                .stream()
                .filter(h -> h.getLatitude() != null && h.getLongitude() != null)
                .filter(verificationService::isFullyVerified)
                .map(h -> new NearbyHospital(h.getId(), h.getHospitalName(), h.getCity(), h.getPhone(),
                        h.getLatitude(), h.getLongitude(),
                        round(GeoUtils.haversineKm(h.getLatitude(), h.getLongitude(), lat, lon))))
                .filter(h -> h.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyHospital::distanceKm))
                .toList();
    }

    private static BigDecimal approximate(BigDecimal coordinate) {
        return coordinate.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round(double km) {
        return Math.round(km * 10.0) / 10.0;
    }
}
