package com.pfe.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Shared helper for tests that exercise service-layer methods relying on
 * {@code SecurityContextHolder} for the current-user email/role (e.g.
 * {@code CaseService.getCurrentUsername()}, {@code SttStockService.currentUser()}).
 *
 * Extracted because every service test that touches auth was hand-rolling
 * the same {@code UsernamePasswordAuthenticationToken} + manual clear.
 */
public final class SecurityTestSupport {

    private SecurityTestSupport() {
    }

    public static void authenticateAs(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of(() -> role))
        );
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
