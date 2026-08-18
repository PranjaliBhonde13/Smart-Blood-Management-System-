package com.sben.bloodrequest.dto;

import com.sben.bloodrequest.BloodRequest;
import com.sben.bloodrequest.RequestStatus;
import com.sben.bloodrequest.Urgency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BloodRequestResponse(
        Long id, Long patientId, Long hospitalId, String bloodGroup, Integer unitsRequired,
        Urgency urgency, String medicalReport, RequestStatus status,
        BigDecimal latitude, BigDecimal longitude, LocalDateTime requestDate
) {
    public static BloodRequestResponse from(BloodRequest r) {
        return new BloodRequestResponse(r.getId(), r.getPatientId(), r.getHospitalId(),
                r.getBloodGroup(), r.getUnitsRequired(), r.getUrgency(), r.getMedicalReport(),
                r.getStatus(), r.getLatitude(), r.getLongitude(), r.getRequestDate());
    }
}
