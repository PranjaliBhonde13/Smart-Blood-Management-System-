package com.sben.notification.email;

/** A rendered email ready for the transport. {@code id} is the outbox row, used as a tracking header. */
public record OutgoingEmail(Long id, String to, String subject, String html, String text) {}
