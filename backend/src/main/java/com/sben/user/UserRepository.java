package com.sben.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Page<User> findByDeletedFalse(Pageable pageable);

    Page<User> findByRoleAndDeletedFalse(Role role, Pageable pageable);
}
