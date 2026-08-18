package com.sben.patient.dto;

import com.sben.patient.Patient;

public record PatientResponse(
        Long id, Long userId, String bloodGroup, Integer age,
        String gender, String address, String emergencyContact
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.getId(), p.getUserId(), p.getBloodGroup(),
                p.getAge(), p.getGender(), p.getAddress(), p.getEmergencyContact());
    }
}
