package com.sben.hospital.document;

/**
 * Review state of one uploaded document. Only an administrator moves a
 * document to VERIFIED or REJECTED; uploads always start PENDING. EXPIRED is
 * also derived live from the expiry date (see {@link HospitalDocument#effectiveStatus()}),
 * so an expired document is never treated as valid even before the nightly job runs.
 */
public enum DocumentStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    EXPIRED
}
