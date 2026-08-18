package com.sben.bloodrequest;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Blood request lifecycle. The {@link #ALLOWED_TRANSITIONS} map is the single
 * source of truth for which state changes are legal — the service rejects any
 * move not listed here (returns 422), so the workflow can never be corrupted.
 *
 * <pre>
 * PENDING → VERIFIED → APPROVED → FULFILLED → COMPLETED
 *    │          │          │           │
 *    └──────────┴──────────┴───────────┴──→ REJECTED / CANCELLED
 * </pre>
 */
public enum RequestStatus {
    PENDING,
    VERIFIED,
    APPROVED,
    FULFILLED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    /** Terminal states — a request here can no longer change. */
    public static final Set<RequestStatus> TERMINAL =
            EnumSet.of(COMPLETED, REJECTED, CANCELLED);

    public static final Map<RequestStatus, Set<RequestStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING,   EnumSet.of(VERIFIED, REJECTED, CANCELLED),
            VERIFIED,  EnumSet.of(APPROVED, REJECTED, CANCELLED),
            APPROVED,  EnumSet.of(FULFILLED, REJECTED, CANCELLED),
            FULFILLED, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(RequestStatus.class),
            REJECTED,  EnumSet.noneOf(RequestStatus.class),
            CANCELLED, EnumSet.noneOf(RequestStatus.class)
    );

    public boolean canTransitionTo(RequestStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
