package com.sben.bloodbank;

/**
 * Blood bank license verification state, derived from the entity (never set by
 * the client). NOT_SUBMITTED means no license certificate has been uploaded yet.
 */
public enum LicenseStatus {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}
