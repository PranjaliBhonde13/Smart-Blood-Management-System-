package com.sben.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Data access for {@link User}. Queries exclude soft-deleted rows by convention.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // --- admin: counts + listing ---
    long countByDeletedFalse();

    long countByRoleAndDeletedFalse(Role role);

    long countByRoleAndEnabledTrueAndDeletedFalse(Role role);

    Page<User> findByDeletedFalse(Pageable pageable);

    Page<User> findByRoleAndDeletedFalse(Role role, Pageable pageable);

    /** Admin user search; every filter is optional (null = ignore). */
    @Query("""
            SELECT u FROM User u
            WHERE u.deleted = false
              AND (:role IS NULL OR u.role = :role)
              AND (:enabled IS NULL OR u.enabled = :enabled)
              AND (:q IS NULL OR LOWER(u.fullName) LIKE :q OR LOWER(u.email) LIKE :q OR u.phone LIKE :q)
            """)
    Page<User> search(@Param("role") Role role, @Param("enabled") Boolean enabled,
                      @Param("q") String q, Pageable pageable);
}
