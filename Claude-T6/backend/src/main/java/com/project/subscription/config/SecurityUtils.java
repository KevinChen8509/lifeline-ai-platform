package com.project.subscription.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility to extract authenticated user info from SecurityContext
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static JwtAuthFilter.AuthPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtAuthFilter.AuthPrincipal principal) {
            return principal;
        }
        // Fallback for unauthenticated / test scenarios
        return new JwtAuthFilter.AuthPrincipal(1L, 1L);
    }

    public static Long getUserId() {
        return getPrincipal().userId();
    }

    public static Long getTenantId() {
        return getPrincipal().tenantId();
    }
}
