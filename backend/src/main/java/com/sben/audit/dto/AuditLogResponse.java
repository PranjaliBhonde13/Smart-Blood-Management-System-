package com.sben.audit.dto;

import com.sben.audit.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id, Long actorUserId, String action, String entityType,
        Long entityId, String details, String ipAddress, LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
    }
}
