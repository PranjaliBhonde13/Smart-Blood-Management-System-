package com.sben.hospital.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalDocumentRequirementRepository extends JpaRepository<HospitalDocumentRequirement, Long> {

    List<HospitalDocumentRequirement> findByActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc();

    List<HospitalDocumentRequirement> findByDeletedFalseOrderBySortOrderAscIdAsc();
}
