package com.sben.bloodrequest.dto;

import com.sben.bloodrequest.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Blood request view. {@link #from} is the full view (requester, hospitals,
 * admins); {@link #redacted} drops recipient-identifying details for donors and
 * blood banks, who only need the clinical need and location.
 */
public record BloodRequestResponse(
        Long id, Long patientId, Long hospitalId, String bloodGroup, Integer unitsRequired,
        Urgency urgency, String medicalReport, RequestStatus status,
        BigDecimal latitude, BigDecimal longitude, LocalDateTime requestDate,
        RequestFor requestFor, String recipientName, Integer recipientAge, String recipientRelationship,
        BloodComponent component, LocalDateTime requiredBy,
        Long preferredHospitalId, String preferredHospitalName, String locationNotes,
        BloodGroupSource bloodGroupSource, boolean bloodGroupConfirmed, LocalDateTime bloodGroupConfirmedAt,
        boolean bloodGroupApprovable
) {
    public static BloodRequestResponse from(BloodRequest r) {
        return new BloodRequestResponse(r.getId(), r.getPatientId(), r.getHospitalId(),
                r.getBloodGroup(), r.getUnitsRequired(), r.getUrgency(), r.getMedicalReport(),
                r.getStatus(), r.getLatitude(), r.getLongitude(), r.getRequestDate(),
                r.getRequestFor(), r.getRecipientName(), r.getRecipientAge(), r.getRecipientRelationship(),
                r.getComponent(), r.getRequiredBy(),
                r.getPreferredHospitalId(), r.getPreferredHospitalName(), r.getLocationNotes(),
                r.getBloodGroupSource(), r.getBloodGroupConfirmedAt() != null, r.getBloodGroupConfirmedAt(),
                r.isBloodGroupApprovable());
    }

    /** Without the recipient's name, age, relationship, notes or medical report reference. */
    public static BloodRequestResponse redacted(BloodRequest r) {
        return new BloodRequestResponse(r.getId(), null, r.getHospitalId(),
                r.getBloodGroup(), r.getUnitsRequired(), r.getUrgency(), null,
                r.getStatus(), r.getLatitude(), r.getLongitude(), r.getRequestDate(),
                null, null, null, null,
                r.getComponent(), r.getRequiredBy(),
                r.getPreferredHospitalId(), r.getPreferredHospitalName(), null,
                null, r.getBloodGroupConfirmedAt() != null, null,
                r.isBloodGroupApprovable());
    }
}
