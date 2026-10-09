package com.sben.notification.email;

/** Delivery state of one outbox email. */
public enum EmailStatus {
    /** Waiting to be sent (first attempt or a retry after {@code nextAttemptAt}). */
    PENDING,
    /** Claimed by the dispatcher and being handed to the mail server. */
    SENDING,
    /** Accepted by the mail server. */
    SENT,
    /** Gave up: a permanent rejection or too many failed attempts. */
    FAILED,
    /** Never sent on purpose: test/demo domain or an invalid address. */
    SUPPRESSED
}
