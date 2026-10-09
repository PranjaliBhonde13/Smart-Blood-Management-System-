package com.sben.donation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DonationHistoryRepository extends JpaRepository<DonationHistory, Long> {

    List<DonationHistory> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    List<DonationHistory> findByRequestIdOrderByCreatedAtDesc(Long requestId);

    Optional<DonationHistory> findFirstByRequestIdAndStatus(Long requestId, DonationHistory.Status status);

    boolean existsByRequestIdAndStatus(Long requestId, DonationHistory.Status status);

    List<DonationHistory> findByRequestIdAndStatus(Long requestId, DonationHistory.Status status);

    /** Most recent donation of this donor with the given status, by donation date. */
    Optional<DonationHistory> findFirstByDonorIdAndStatusOrderByDonationDateDesc(Long donorId, DonationHistory.Status status);

    // --- admin / reports ---
    long countByStatus(DonationHistory.Status status);

    long countByDonorIdAndStatus(Long donorId, DonationHistory.Status status);

    long countByBloodBankIdAndStatus(Long bloodBankId, DonationHistory.Status status);

    List<DonationHistory> findTop20ByDonorIdOrderByCreatedAtDesc(Long donorId);
}
