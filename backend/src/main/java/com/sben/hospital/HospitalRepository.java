package com.sben.hospital;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findByUserId(Long userId);

    boolean existsByLicenseNumber(String licenseNumber);

    // --- admin: verification queue ---
    java.util.List<Hospital> findByVerifiedFalseAndDeletedFalse();

    long countByVerifiedFalseAndDeletedFalse();

    /** Verified hospitals within a lat/long bounding box (geo prefilter). */
    @Query("""
            SELECT h FROM Hospital h
            WHERE h.deleted = false
              AND h.verified = true
              AND h.latitude BETWEEN :minLat AND :maxLat
              AND h.longitude BETWEEN :minLon AND :maxLon
            """)
    List<Hospital> findVerifiedInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                     @Param("minLon") double minLon, @Param("maxLon") double maxLon);
}
