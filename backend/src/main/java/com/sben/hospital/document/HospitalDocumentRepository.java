package com.sben.hospital.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface HospitalDocumentRepository extends JpaRepository<HospitalDocument, Long> {

    List<HospitalDocument> findByHospitalIdAndCurrentTrueAndDeletedFalseOrderByUploadedAtDesc(Long hospitalId);

    /** Full submission history, newest first. */
    List<HospitalDocument> findByHospitalIdAndDeletedFalseOrderByUploadedAtDesc(Long hospitalId);

    List<HospitalDocument> findByHospitalIdAndDocumentTypeAndCurrentTrueAndDeletedFalse(Long hospitalId,
                                                                                         HospitalDocumentType type);

    List<HospitalDocument> findByDocumentRequestIdAndCurrentTrueAndDeletedFalse(Long documentRequestId);

    /** Hospitals with at least one current, unexpired document awaiting review. */
    @Query("""
            SELECT DISTINCT d.hospitalId FROM HospitalDocument d
            WHERE d.current = true AND d.deleted = false
              AND d.status = com.sben.hospital.document.DocumentStatus.PENDING
              AND (d.expiryDate IS NULL OR d.expiryDate >= :today)
            """)
    List<Long> findHospitalIdsWithPendingReview(@Param("today") LocalDate today);

    /** Current pending/approved documents whose expiry date has passed (nightly job). */
    @Query("""
            SELECT d FROM HospitalDocument d
            WHERE d.current = true AND d.deleted = false AND d.status IN :statuses
              AND d.expiryDate IS NOT NULL AND d.expiryDate < :today
            """)
    List<HospitalDocument> findNewlyExpired(@Param("statuses") Collection<DocumentStatus> statuses,
                                            @Param("today") LocalDate today);

    /** Current approved documents expiring within the warning window that have not been warned about yet. */
    @Query("""
            SELECT d FROM HospitalDocument d
            WHERE d.current = true AND d.deleted = false
              AND d.status = com.sben.hospital.document.DocumentStatus.VERIFIED
              AND d.expiryDate IS NOT NULL AND d.expiryDate >= :today AND d.expiryDate <= :until
              AND d.expiryWarningSentAt IS NULL
            """)
    List<HospitalDocument> findExpiringSoon(@Param("today") LocalDate today, @Param("until") LocalDate until);
}
