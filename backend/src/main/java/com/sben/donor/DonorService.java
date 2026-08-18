package com.sben.donor;

import com.sben.common.exception.ResourceNotFoundException;
import com.sben.donor.dto.DonorRequest;
import com.sben.donor.dto.DonorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the caller's own donor profile plus the quick availability toggle
 * used from the donor dashboard.
 */
@Service
@RequiredArgsConstructor
public class DonorService {

    private final DonorRepository repository;

    @Transactional(readOnly = true)
    public DonorResponse getMyProfile(Long userId) {
        return repository.findByUserId(userId)
                .map(DonorResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found. Complete your profile."));
    }

    @Transactional
    public DonorResponse upsertMyProfile(Long userId, DonorRequest req) {
        Donor donor = repository.findByUserId(userId)
                .orElseGet(() -> Donor.builder().userId(userId).availability(Availability.AVAILABLE).build());
        donor.setBloodGroup(req.bloodGroup());
        donor.setAge(req.age());
        donor.setCity(req.city());
        donor.setLatitude(req.latitude());
        donor.setLongitude(req.longitude());
        donor.setLastDonationDate(req.lastDonationDate());
        return DonorResponse.from(repository.save(donor));
    }

    @Transactional
    public DonorResponse setAvailability(Long userId, Availability availability) {
        Donor donor = repository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found. Complete your profile."));
        donor.setAvailability(availability);
        return DonorResponse.from(repository.save(donor));
    }
}
