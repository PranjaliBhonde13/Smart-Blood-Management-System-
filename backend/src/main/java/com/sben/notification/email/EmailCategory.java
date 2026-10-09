package com.sben.notification.email;

/**
 * Why an email is sent. Decides delivery priority and whether a user's
 * preferences can switch it off: security and account emails always go out,
 * activity and announcement emails respect opt-outs.
 */
public enum EmailCategory {
    /** One-time codes, password changes, new sign-ins. Always sent, highest priority. */
    SECURITY(0),
    /** Welcome, suspension and restoration of the account. Always sent. */
    ACCOUNT(1),
    /** Profile, document and licence verification outcomes. Always sent. */
    VERIFICATION(1),
    /** Blood request activity. */
    REQUEST(2),
    /** Donation activity. */
    DONATION(2),
    /** Administrator broadcasts. Lowest priority, so they never delay the rest. */
    ANNOUNCEMENT(3);

    private final int priority;

    EmailCategory(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
