package com.sben.donation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestDeclineRepository extends JpaRepository<RequestDecline, Long> {

    boolean existsByRequestIdAndDonorId(Long requestId, Long donorId);
}
