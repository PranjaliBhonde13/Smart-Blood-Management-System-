package com.sben.notification.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer("Smart Blood Emergency Network", "https://app.example.in/");

    @Test
    void escapesUserContentSoItCannotInjectMarkup() {
        EmailRenderer.Rendered r = renderer.render(EmailContent.builder()
                .subject("Hello <b>x</b>")
                .heading("<script>alert(1)</script>")
                .paragraph("Line one\nLine \"two\" & more")
                .detail(new EmailContent.Detail("Reason", "<img src=x onerror=alert(1)>"))
                .build(), EmailCategory.ACCOUNT);
        assertFalse(r.html().contains("<script>"));
        assertFalse(r.html().contains("<img src=x"));
        assertTrue(r.html().contains("&lt;script&gt;"));
        assertTrue(r.html().contains("Line one<br>Line &quot;two&quot; &amp; more"));
    }

    @Test
    void includesCodeButtonAndPlainTextAlternative() {
        EmailRenderer.Rendered r = renderer.render(EmailContent.builder()
                .subject("Verify").heading("Confirm").paragraph("Use this code")
                .code("123456").codeNote("Expires in 10 minutes")
                .actionLabel("Sign in").actionUrl(renderer.link("/login"))
                .build(), EmailCategory.SECURITY);
        assertTrue(r.html().contains("123456"));
        assertTrue(r.html().contains("href=\"https://app.example.in/login\""));
        assertTrue(r.text().contains("123456"));
        assertTrue(r.text().contains("Sign in: https://app.example.in/login"));
        assertFalse(r.text().contains("<"), "plain text has no markup");
    }

    @Test
    void dropsNonHttpLinksAndBlankDetails() {
        EmailRenderer.Rendered r = renderer.render(EmailContent.builder()
                .subject("s").actionLabel("Click").actionUrl("javascript:alert(1)")
                .detail(new EmailContent.Detail("Empty", " "))
                .build(), EmailCategory.REQUEST);
        assertFalse(r.html().contains("javascript:"));
        assertFalse(r.html().contains("Empty"));
        assertFalse(r.text().contains("Click"));
    }

    @Test
    void footerExplainsWhyTheEmailWasSent() {
        assertTrue(renderer.render(EmailContent.builder().subject("s").build(), EmailCategory.ANNOUNCEMENT)
                .text().contains("announcement"));
        assertTrue(renderer.render(EmailContent.builder().subject("s").build(), EmailCategory.SECURITY)
                .text().contains("security"));
    }
}
