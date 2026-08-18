package com.sben.bloodrequest;

/** Triage level for a blood request; drives queue ordering and alerting. */
public enum Urgency {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
