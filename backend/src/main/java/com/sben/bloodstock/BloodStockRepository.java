package com.sben.bloodstock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BloodStockRepository extends JpaRepository<BloodStock, Long> {

    /** Total units across all banks — admin dashboard KPI. */
    @Query("SELECT COALESCE(SUM(s.unitsAvailable), 0) FROM BloodStock s WHERE s.deleted = false")
    long totalUnits();

    List<BloodStock> findByBloodBankIdAndDeletedFalse(Long bloodBankId);

    Optional<BloodStock> findByBloodBankIdAndBloodGroupAndDeletedFalse(Long bloodBankId, String bloodGroup);

    /**
     * Row-locked stock lines of one bank for the given groups. Used when issuing
     * blood so two concurrent fulfilments can never spend the same units.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM BloodStock s
            WHERE s.bloodBankId = :bankId AND s.bloodGroup IN :groups AND s.deleted = false
            """)
    List<BloodStock> lockByBankAndGroups(@Param("bankId") Long bankId,
                                         @Param("groups") Collection<String> groups);

    /** Units held by one bank (inventory summary scoped to the caller). */
    @Query("SELECT COALESCE(SUM(s.unitsAvailable), 0) FROM BloodStock s WHERE s.bloodBankId = :bankId AND s.deleted = false")
    long totalUnitsForBank(@Param("bankId") Long bankId);

    long countByBloodBankIdAndDeletedFalse(Long bloodBankId);

    long countByDeletedFalse();

    /** Stock lines for any of the given groups that still have units — for bank search. */
    List<BloodStock> findByBloodGroupInAndUnitsAvailableGreaterThanAndDeletedFalse(
            Collection<String> bloodGroups, int minUnits);
}
