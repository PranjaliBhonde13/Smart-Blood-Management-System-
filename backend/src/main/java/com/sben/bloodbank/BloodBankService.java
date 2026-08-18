package com.sben.bloodbank;

import com.sben.bloodbank.dto.BloodBankRequest;
import com.sben.bloodbank.dto.BloodBankResponse;
import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the caller's own blood bank profile. Verification is admin-only (M6). */
@Service
@RequiredArgsConstructor
public class BloodBankService {

    private final BloodBankRepository repository;

    @Transactional(readOnly = true)
    public BloodBankResponse getMyProfile(Long userId) {
        return repository.findByUserId(userId)
                .map(BloodBankResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank profile not found. Complete your profile."));
    }

    @Transactional
    public BloodBankResponse upsertMyProfile(Long userId, BloodBankRequest req) {
        BloodBank bank = repository.findByUserId(userId).orElse(null);
        if (bank == null) {
            if (repository.existsByLicenseNumber(req.licenseNumber())) {
                throw new BusinessException("License number already registered", HttpStatus.CONFLICT);
            }
            bank = BloodBank.builder().userId(userId).verified(false).build();
        }
        bank.setBankName(req.bankName());
        bank.setLicenseNumber(req.licenseNumber());
        bank.setPhone(req.phone());
        bank.setAddress(req.address());
        bank.setCity(req.city());
        bank.setLatitude(req.latitude());
        bank.setLongitude(req.longitude());
        return BloodBankResponse.from(repository.save(bank));
    }
}
