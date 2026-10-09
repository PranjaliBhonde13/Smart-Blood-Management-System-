package com.sben.hospital.document;

/** State of one requirement (or administrator request) for a hospital. */
public enum RequirementState {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}
