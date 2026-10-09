package com.sben.patient.document;

/**
 * Review state of one uploaded document. Uploads always start PENDING; only an
 * administrator moves a document to VERIFIED, REJECTED or ACTION_REQUIRED
 * ("upload a corrected document").
 */
public enum PatientDocumentStatus {
    PENDING,
    VERIFIED,
    REJECTED,
    ACTION_REQUIRED
}
