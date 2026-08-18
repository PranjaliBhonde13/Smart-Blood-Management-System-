package com.sben.patient;

import com.sben.common.exception.ResourceNotFoundException;
import com.sben.patient.dto.PatientRequest;
import com.sben.patient.dto.PatientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the caller's own patient profile. Upsert semantics: the first PUT
 * creates the profile row, subsequent PUTs update it.
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository repository;

    @Transactional(readOnly = true)
    public PatientResponse getMyProfile(Long userId) {
        return repository.findByUserId(userId)
                .map(PatientResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found. Complete your profile."));
    }

    @Transactional
    public PatientResponse upsertMyProfile(Long userId, PatientRequest req) {
        Patient patient = repository.findByUserId(userId)
                .orElseGet(() -> Patient.builder().userId(userId).build());
        patient.setBloodGroup(req.bloodGroup());
        patient.setAge(req.age());
        patient.setGender(req.gender());
        patient.setAddress(req.address());
        patient.setEmergencyContact(req.emergencyContact());
        return PatientResponse.from(repository.save(patient));
    }
}
