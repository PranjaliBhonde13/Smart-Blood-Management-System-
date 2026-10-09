package com.sben.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /**
     * Latest unused token of a type for a user — used to validate OTP/reset codes.
     * Ordered by id (strictly increasing) because created_at only has second precision.
     */
    Optional<VerificationToken> findFirstByUserIdAndTypeAndUsedFalseOrderByIdDesc(
            Long userId, VerificationToken.Type type);

    /** Retire every outstanding code of a type, so only the newest one ever works. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE VerificationToken t SET t.used = true WHERE t.userId = :userId AND t.type = :type AND t.used = false")
    int invalidateActive(@Param("userId") Long userId, @Param("type") VerificationToken.Type type);
}
