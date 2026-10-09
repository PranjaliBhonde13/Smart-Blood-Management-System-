package com.sben.hospital.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HospitalDocumentRequestRepository extends JpaRepository<HospitalDocumentRequest, Long> {

    List<HospitalDocumentRequest> findByHospitalIdAndStatusInAndDeletedFalseOrderByRequestedAtAsc(
            Long hospitalId, Collection<HospitalDocumentRequest.Status> statuses);

    List<HospitalDocumentRequest> findByHospitalIdAndDeletedFalseOrderByRequestedAtDesc(Long hospitalId);
}
