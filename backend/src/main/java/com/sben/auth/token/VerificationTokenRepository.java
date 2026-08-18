package com.sben.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /** Latest unused token of a type for a user — used to validate OTP/reset codes. */
    Optional<VerificationToken> findFirstByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(
            Long userId, VerificationToken.Type type);
}
