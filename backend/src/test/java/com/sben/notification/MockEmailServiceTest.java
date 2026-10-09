package com.sben.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MockEmailServiceTest {

    private final MockEmailService mockEmailService = new MockEmailService();

    @Test
    void send_ShouldNotThrowException() {
        assertDoesNotThrow(() -> mockEmailService.send("test@example.com", "Subject", "Body"));
    }

    @Test
    void sendHtml_ShouldNotThrowException() {
        assertDoesNotThrow(() -> mockEmailService.sendHtml("test@example.com", "Subject", "<h1>HTML</h1>"));
    }

    @Test
    void sendOtp_ShouldGenerateHtmlAndNotThrowException() {
        assertDoesNotThrow(() -> mockEmailService.sendOtp("test@example.com", "123456", 10));
    }
}
