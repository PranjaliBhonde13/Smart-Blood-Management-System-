package com.sben.bloodrequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {

    Page<BloodRequest> findByPatientIdAndDeletedFalse(Long patientId, Pageable pageable);

    Page<BloodRequest> findByStatusAndDeletedFalse(RequestStatus status, Pageable pageable);

    Page<BloodRequest> findByDeletedFalse(Pageable pageable);

    /** Open (given status) requests for any of the given groups — donor feed. */
    Page<BloodRequest> findByStatusAndBloodGroupInAndDeletedFalse(
            RequestStatus status, Collection<String> bloodGroups, Pageable pageable);

    /** Donor feed minus the requests this donor has already declined. */
    @Query("""
            SELECT r FROM BloodRequest r
            WHERE r.status = :status
              AND r.bloodGroup IN :groups
              AND r.deleted = false
              AND NOT EXISTS (SELECT 1 FROM RequestDecline d
                              WHERE d.requestId = r.id AND d.donorId = :donorId)
            """)
    Page<BloodRequest> findOpenForDonor(@Param("status") RequestStatus status,
                                        @Param("groups") Collection<String> bloodGroups,
                                        @Param("donorId") Long donorId,
                                        Pageable pageable);

    /** Row-locked read so concurrent accept/fulfil calls on one request serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BloodRequest r WHERE r.id = :id")
    Optional<BloodRequest> findByIdForUpdate(@Param("id") Long id);

    // --- admin / reports ---
    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(RequestStatus status);

    long countByRequestDateAfterAndDeletedFalse(LocalDateTime start);

    List<BloodRequest> findTop500ByDeletedFalseOrderByRequestDateDesc();

    /** Admin request monitor; every filter is optional (null = ignore). */
    @Query("""
            SELECT r FROM BloodRequest r
            WHERE r.deleted = false
              AND (:status IS NULL OR r.status = :status)
              AND (:urgency IS NULL OR r.urgency = :urgency)
              AND (:bloodGroup IS NULL OR r.bloodGroup = :bloodGroup)
              AND (:hospitalId IS NULL OR r.hospitalId = :hospitalId)
              AND (:from IS NULL OR r.requestDate >= :from)
              AND (:to IS NULL OR r.requestDate < :to)
            """)
    Page<BloodRequest> adminSearch(@Param("status") RequestStatus status,
                                   @Param("urgency") Urgency urgency,
                                   @Param("bloodGroup") String bloodGroup,
                                   @Param("hospitalId") Long hospitalId,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   Pageable pageable);

    /** Requests still in progress (candidates for "stuck" alerts). */
    List<BloodRequest> findByStatusInAndDeletedFalse(Collection<RequestStatus> statuses);

    /** Requests raised since a moment (analytics window). */
    List<BloodRequest> findByRequestDateGreaterThanEqualAndDeletedFalse(LocalDateTime from);

    List<BloodRequest> findTop20ByPatientIdAndDeletedFalseOrderByRequestDateDesc(Long patientId);

    long countByPatientIdAndDeletedFalse(Long patientId);

    long countByHospitalIdAndDeletedFalse(Long hospitalId);

    /** A hospital's requests in the given states (e.g. still in progress). */
    long countByHospitalIdAndStatusInAndDeletedFalse(Long hospitalId, Collection<RequestStatus> statuses);
}
