package com.sben.patient.document;

/**
 * Overall patient status, derived on the server from the profile and the two
 * document tracks. Priority (highest first): REJECTED, ACTION_REQUIRED,
 * PROFILE_INCOMPLETE, PENDING_VERIFICATION, VERIFIED. VERIFIED means BOTH
 * identity and blood group are verified.
 */
public enum PatientVerificationStatus {
    PROFILE_INCOMPLETE,
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED,
    ACTION_REQUIRED
}
