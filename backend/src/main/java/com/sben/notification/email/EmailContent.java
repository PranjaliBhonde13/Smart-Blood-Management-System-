package com.sben.notification.email;

import lombok.Builder;
import lombok.Singular;

import java.util.List;

/**
 * What an email says, independent of how it looks. {@link EmailRenderer}
 * turns it into the branded HTML layout plus a plain-text alternative. All
 * values are plain text: the renderer escapes them, so user-supplied content
 * (names, reasons, broadcast text) can never inject markup.
 */
@Builder(toBuilder = true)
public record EmailContent(
        String subject,
        /** Inbox preview line shown after the subject. */
        String preheader,
        String heading,
        @Singular List<String> paragraphs,
        /** One-time code shown in a large box. */
        String code,
        String codeNote,
        @Singular List<Detail> details,
        String actionLabel,
        String actionUrl,
        @Singular("afterAction") List<String> afterAction,
        Tone tone
) {
    public enum Tone { NEUTRAL, SUCCESS, ALERT }

    public record Detail(String label, String value) {}

    public Tone toneOrDefault() {
        return tone == null ? Tone.NEUTRAL : tone;
    }
}
