package com.sben.hospital;

import com.sben.common.exception.BusinessException;
import com.sben.common.exception.ResourceNotFoundException;
import com.sben.hospital.dto.HospitalRequest;
import com.sben.hospital.dto.HospitalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the caller's own hospital profile. Verification is admin-only (M6). */
@Service
@RequiredArgsConstructor
public class HospitalService {

    private final HospitalRepository repository;

    @Transactional(readOnly = true)
    public HospitalResponse getMyProfile(Long userId) {
        return repository.findByUserId(userId)
                .map(HospitalResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital profile not found. Complete your profile."));
    }

    @Transactional
    public HospitalResponse upsertMyProfile(Long userId, HospitalRequest req) {
        Hospital hospital = repository.findByUserId(userId).orElse(null);
        if (hospital == null) {
            // License must be unique across hospitals on first registration.
            if (repository.existsByLicenseNumber(req.licenseNumber())) {
                throw new BusinessException("License number already registered", HttpStatus.CONFLICT);
            }
            hospital = Hospital.builder().userId(userId).verified(false).build();
        }
        hospital.setHospitalName(req.hospitalName());
        hospital.setLicenseNumber(req.licenseNumber());
        hospital.setPhone(req.phone());
        hospital.setAddress(req.address());
        hospital.setCity(req.city());
        hospital.setState(req.state());
        hospital.setPincode(req.pincode());
        hospital.setLatitude(req.latitude());
        hospital.setLongitude(req.longitude());
        return HospitalResponse.from(repository.save(hospital));
    }
}
