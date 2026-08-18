package com.sben.notification.dto;

import com.sben.notification.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id, String title, String message,
        Notification.Type type, Notification.Status status, LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(),
                n.getType(), n.getStatus(), n.getCreatedAt());
    }
}
