package com.sben.patient.document;

/**
 * Accepted document types, each belonging to one verification track. Several
 * government IDs are accepted so no single ID (e.g. Aadhaar) is mandatory, and
 * ID numbers themselves are never collected — only the uploaded image/PDF.
 */
public enum PatientDocumentType {
    // ---- identity ----
    AADHAAR(DocumentCategory.IDENTITY, "Aadhaar (masked copy recommended)"),
    PASSPORT(DocumentCategory.IDENTITY, "Passport"),
    VOTER_ID(DocumentCategory.IDENTITY, "Voter ID (EPIC)"),
    DRIVING_LICENCE(DocumentCategory.IDENTITY, "Driving licence"),
    PAN_CARD(DocumentCategory.IDENTITY, "PAN card"),
    OTHER_GOVERNMENT_ID(DocumentCategory.IDENTITY, "Other government-issued photo ID"),
    // ---- blood group ----
    LAB_REPORT(DocumentCategory.BLOOD_GROUP, "Blood-group laboratory report"),
    HOSPITAL_RECORD(DocumentCategory.BLOOD_GROUP, "Hospital / authorised medical record"),
    BLOOD_GROUP_CARD(DocumentCategory.BLOOD_GROUP, "Blood-group card issued by a lab or blood bank");

    private final DocumentCategory category;
    private final String label;

    PatientDocumentType(DocumentCategory category, String label) {
        this.category = category;
        this.label = label;
    }

    public DocumentCategory category() { return category; }
    public String label() { return label; }
}
