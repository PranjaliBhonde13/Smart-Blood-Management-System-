package com.sben.patient.document;

/** Status of one verification track: the current document's status, or NOT_SUBMITTED. */
public enum TrackStatus {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED,
    REJECTED,
    ACTION_REQUIRED;

    public static TrackStatus of(PatientDocument current) {
        return current == null ? NOT_SUBMITTED : TrackStatus.valueOf(current.getStatus().name());
    }
}
