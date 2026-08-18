package com.sben.audit;

import com.sben.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes and reads audit entries. {@link #record} captures the current actor and
 * client IP automatically so callers only describe the action.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(String action, String entityType, Long entityId, String details) {
        repository.save(AuditLog.builder()
                .actorUserId(currentUserId())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(clientIp())
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) {
        return repository.findByOrderByCreatedAtDesc(pageable);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) return p.getId();
        return null;
    }

    private String clientIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getRemoteAddr();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
