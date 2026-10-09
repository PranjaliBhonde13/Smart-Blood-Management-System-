package com.sben.bloodrequest;

/**
 * Where a request's blood group came from, set by the server (never the client).
 * PROFILE_VERIFIED: the requester's own group, backed by an admin-verified document.
 * PROFILE_UNVERIFIED: the requester's own group, not yet verified.
 * DECLARED: typed in for another patient.
 * Only PROFILE_VERIFIED may be approved without hospital/admin confirmation.
 */
public enum BloodGroupSource {
    PROFILE_VERIFIED,
    PROFILE_UNVERIFIED,
    DECLARED
}
