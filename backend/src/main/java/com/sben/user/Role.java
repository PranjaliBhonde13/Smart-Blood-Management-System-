package com.sben.user;

/**
 * System roles. Mirrors the {@code users.role} ENUM in the database and drives
 * all role-based access control (RBAC) on both API and UI.
 */
public enum Role {
    PATIENT,
    DONOR,
    HOSPITAL,
    BLOOD_BANK,
    ADMIN
}
