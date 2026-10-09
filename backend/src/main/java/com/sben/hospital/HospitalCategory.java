package com.sben.hospital;

/**
 * Registration category a hospital declares on its profile. Document
 * requirements can be configured per category ({@code hospital_document_requirements});
 * a requirement with no category applies to every category.
 */
public enum HospitalCategory {
    GOVERNMENT_HOSPITAL,
    PRIVATE_HOSPITAL,
    NURSING_HOME,
    TRUST_OR_CHARITABLE,
    MEDICAL_COLLEGE,
    CLINIC,
    OTHER
}
