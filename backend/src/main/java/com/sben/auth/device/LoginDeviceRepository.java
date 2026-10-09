package com.sben.auth.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginDeviceRepository extends JpaRepository<LoginDevice, Long> {

    boolean existsByUserId(Long userId);

    Optional<LoginDevice> findByUserIdAndFingerprint(Long userId, String fingerprint);
}
