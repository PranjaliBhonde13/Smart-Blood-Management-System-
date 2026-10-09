package com.sben.hospital.document;

/**
 * Overall status shown to the hospital and admins. Profile completion is
 * reported separately; this is about whether the hospital may act on the network.
 */
public enum HospitalVerificationStatus {
    PROFILE_INCOMPLETE,
    DOCUMENTS_PENDING,
    ACTION_REQUIRED,
    VERIFICATION_EXPIRED,
    VERIFIED
}
