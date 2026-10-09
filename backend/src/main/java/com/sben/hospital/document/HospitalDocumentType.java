package com.sben.hospital.document;

/**
 * Kinds of hospital document. A single-slot type has at most one current
 * document (uploading again replaces it); the others may hold several current
 * documents side by side. ADDITIONAL_REQUESTED is only uploaded against an
 * administrator's document request.
 */
public enum HospitalDocumentType {
    REGISTRATION_CERTIFICATE("Hospital registration certificate", true),
    OPERATING_LICENSE("Operating license / registration proof", true),
    BLOOD_BANK_LICENSE("Blood bank license", true),
    ACCREDITATION_CERTIFICATE("Accreditation certificate", false),
    SUPPORTING_DOCUMENT("Supporting document", false),
    ADDITIONAL_REQUESTED("Document requested by administrator", false);

    private final String label;
    private final boolean singleSlot;

    HospitalDocumentType(String label, boolean singleSlot) {
        this.label = label;
        this.singleSlot = singleSlot;
    }

    public String label() {
        return label;
    }

    public boolean singleSlot() {
        return singleSlot;
    }
}
