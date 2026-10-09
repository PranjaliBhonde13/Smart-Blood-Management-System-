package com.sben.audit;

import com.sben.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;

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

    /** Filtered audit search (all filters optional). {@code to} is inclusive (whole day). */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, Long actorUserId, String entityType,
                                 LocalDate from, LocalDate to, Pageable pageable) {
        return repository.search(blankToNull(action), actorUserId, blankToNull(entityType),
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                pageable);
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return repository.distinctActions();
    }

    /** CSV of the filtered audit trail (capped at 5000 rows). */
    @Transactional(readOnly = true)
    public String exportCsv(String action, Long actorUserId, String entityType, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder("id,createdAt,actorUserId,action,entityType,entityId,details,ipAddress\n");
        for (AuditLog a : search(action, actorUserId, entityType, from, to, PageRequest.of(0, 5000))) {
            sb.append(a.getId()).append(',')
              .append(a.getCreatedAt()).append(',')
              .append(a.getActorUserId() == null ? "" : a.getActorUserId()).append(',')
              .append(csv(a.getAction())).append(',')
              .append(csv(a.getEntityType())).append(',')
              .append(a.getEntityId() == null ? "" : a.getEntityId()).append(',')
              .append(csv(a.getDetails())).append(',')
              .append(csv(a.getIpAddress())).append('\n');
        }
        return sb.toString();
    }

    /** Quote a CSV field and neutralise spreadsheet formula injection. */
    private static String csv(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
