package com.sben.bloodrequest;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * How long a request may sit in one in-progress status before admins are
 * alerted, by urgency. A request is "stuck" once it exceeds its limit while
 * PENDING, VERIFIED or APPROVED (waiting on a hospital or a donor).
 */
public final class RequestSla {

    private RequestSla() {}

    /** Statuses where the request is waiting on someone. */
    public static final Set<RequestStatus> WAITING =
            EnumSet.of(RequestStatus.PENDING, RequestStatus.VERIFIED, RequestStatus.APPROVED);

    private static final Map<Urgency, Long> LIMIT_MINUTES = Map.of(
            Urgency.CRITICAL, 30L,
            Urgency.HIGH, 120L,
            Urgency.MEDIUM, 12L * 60,
            Urgency.LOW, 48L * 60
    );

    public static long limitMinutes(Urgency urgency) {
        return LIMIT_MINUTES.getOrDefault(urgency, 12L * 60);
    }

    public static boolean isOverdue(RequestStatus status, Urgency urgency, long minutesInStatus) {
        return WAITING.contains(status) && minutesInStatus > limitMinutes(urgency);
    }
}
