package com.sben.bloodbank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BloodBankRepository extends JpaRepository<BloodBank, Long> {
    Optional<BloodBank> findByUserId(Long userId);
    boolean existsByLicenseNumber(String licenseNumber);
    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);
    List<BloodBank> findByIdInAndDeletedFalse(Collection<Long> ids);

    // --- admin: verification queue ---
    List<BloodBank> findByVerifiedFalseAndDeletedFalse();
    List<BloodBank> findByVerifiedFalseAndRejectedAtIsNullAndDeletedFalse();
    long countByVerifiedFalseAndRejectedAtIsNullAndDeletedFalse();
    List<BloodBank> findByVerifiedFalseAndRejectedAtIsNullAndLicenseDocumentIsNotNullAndDeletedFalse();
    long countByVerifiedFalseAndRejectedAtIsNullAndLicenseDocumentIsNotNullAndDeletedFalse();
    long countByVerifiedFalseAndDeletedFalse();
}
