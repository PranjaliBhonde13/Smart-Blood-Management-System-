package com.sben.hospital.document;

/** When a configured requirement applies to a hospital. */
public enum RequirementCondition {
    /** Every hospital in the requirement's category. */
    ALWAYS,
    /** Only hospitals that operate their own blood bank (not hospitals that only request blood). */
    OPERATES_BLOOD_BANK
}
