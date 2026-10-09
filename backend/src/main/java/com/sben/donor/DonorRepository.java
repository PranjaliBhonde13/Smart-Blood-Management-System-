package com.sben.donor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Matching queries return only donors who can be contacted: available, not
 * deleted, and not opted out of donation requests. {@code contactConsent IS NULL}
 * (profiles saved before consent was asked) keeps the previous behaviour until
 * the donor answers.
 */
public interface DonorRepository extends JpaRepository<Donor, Long> {

    Optional<Donor> findByUserId(Long userId);

    /** Contactable donors of compatible groups when the request has no location (cap with {@code page}). */
    @Query("""
            SELECT d FROM Donor d
            WHERE d.deleted = false
              AND d.availability = com.sben.donor.Availability.AVAILABLE
              AND (d.contactConsent IS NULL OR d.contactConsent = true)
              AND d.bloodGroup IN :groups
            """)
    List<Donor> findContactable(@Param("groups") Collection<String> groups, Pageable page);

    /**
     * Contactable donors of compatible groups within a lat/long bounding box.
     * The box is an indexed prefilter; exact Haversine distance is applied in
     * the service (see GeoService / GeoUtils).
     */
    @Query("""
            SELECT d FROM Donor d
            WHERE d.deleted = false
              AND d.availability = com.sben.donor.Availability.AVAILABLE
              AND (d.contactConsent IS NULL OR d.contactConsent = true)
              AND d.bloodGroup IN :groups
              AND d.latitude BETWEEN :minLat AND :maxLat
              AND d.longitude BETWEEN :minLon AND :maxLon
            """)
    List<Donor> findAvailableInBox(@Param("groups") Collection<String> groups,
                                   @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                   @Param("minLon") double minLon, @Param("maxLon") double maxLon);
}
