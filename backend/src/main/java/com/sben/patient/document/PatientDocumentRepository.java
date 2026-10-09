package com.sben.patient.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    Optional<PatientDocument> findFirstByPatientIdAndCategoryAndCurrentTrueAndDeletedFalse(Long patientId, DocumentCategory category);

    List<PatientDocument> findByPatientIdAndCurrentTrueAndDeletedFalse(Long patientId);

    List<PatientDocument> findByPatientIdAndDeletedFalseOrderByUploadedAtDesc(Long patientId);

    /** Patients with at least one current document in one of these states (admin queue). */
    @Query("select distinct d.patientId from PatientDocument d "
            + "where d.current = true and d.deleted = false and d.status in :statuses")
    List<Long> findPatientIdsWithCurrentStatusIn(Collection<PatientDocumentStatus> statuses);
}
