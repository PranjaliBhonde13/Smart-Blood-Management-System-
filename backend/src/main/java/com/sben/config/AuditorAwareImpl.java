package com.sben.config;

import com.sben.security.UserPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current user's id to JPA auditing so {@code created_by}/
 * {@code updated_by} are set automatically on every save. Returns empty for
 * unauthenticated actions (e.g. self-registration), leaving the columns null.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal.getId());
        }
        return Optional.empty();
    }
}
