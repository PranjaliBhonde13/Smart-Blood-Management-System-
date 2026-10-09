package com.sben.notification.email;

/**
 * The mail server did not accept an email. {@code permanent} failures (an
 * invalid or rejected address) are not retried; anything else is retried with
 * backoff by the dispatcher.
 */
public class EmailDeliveryException extends RuntimeException {

    private final boolean permanent;

    public EmailDeliveryException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
