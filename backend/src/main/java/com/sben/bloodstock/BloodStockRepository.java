package com.sben.bloodstock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BloodStockRepository extends JpaRepository<BloodStock, Long> {

    /** Total units across all banks — admin dashboard KPI. */
    @Query("SELECT COALESCE(SUM(s.unitsAvailable), 0) FROM BloodStock s WHERE s.deleted = false")
    long totalUnits();

    List<BloodStock> findByBloodBankIdAndDeletedFalse(Long bloodBankId);

    Optional<BloodStock> findByBloodBankIdAndBloodGroupAndDeletedFalse(Long bloodBankId, String bloodGroup);

    /** Stock lines for any of the given groups that still have units — for bank search. */
    List<BloodStock> findByBloodGroupInAndUnitsAvailableGreaterThanAndDeletedFalse(
            Collection<String> bloodGroups, int minUnits);
}
