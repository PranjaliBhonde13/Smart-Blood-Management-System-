package com.sben.bloodrequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {

    Page<BloodRequest> findByPatientIdAndDeletedFalse(Long patientId, Pageable pageable);

    Page<BloodRequest> findByStatusAndDeletedFalse(RequestStatus status, Pageable pageable);

    Page<BloodRequest> findByDeletedFalse(Pageable pageable);

    /** Open (given status) requests for any of the given groups — donor feed. */
    Page<BloodRequest> findByStatusAndBloodGroupInAndDeletedFalse(
            RequestStatus status, Collection<String> bloodGroups, Pageable pageable);

    // --- admin / reports ---
    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(RequestStatus status);

    long countByRequestDateAfterAndDeletedFalse(LocalDateTime start);

    List<BloodRequest> findTop500ByDeletedFalseOrderByRequestDateDesc();
}
