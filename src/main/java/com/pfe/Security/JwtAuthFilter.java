package com.pfe.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String BANNED_MESSAGE = "Unfortunately you are banned! Contact admin";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Never log the raw Authorization header or token: it is a bearer
        // credential — logging it (even at DEBUG, even to local stdout)
        // creates a credential-leak vector if logs are ever centralized,
        // persisted, or read by another process/user. Only presence/absence
        // and non-sensitive outcome metadata are logged, at DEBUG so it's
        // silent by default in prod (logging.level.com.pfe=INFO there) and
        // visible in dev (logging.level.com.pfe=DEBUG).
        log.debug("JWT filter: path={}", path);

        if (header == null || !header.startsWith("Bearer ")) {
            log.debug("JWT filter: no bearer token for path={}", path);
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        if (!jwtService.isValid(token)) {
            log.debug("JWT filter: invalid token for path={}", path);
            chain.doFilter(request, response);
            return;
        }

        String email = jwtService.extractSubject(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Session invalidation: a token whose embedded version is behind the
            // live account (e.g. after a password reset bumped tokenVersion) is
            // rejected here, so every previously issued access token stops
            // working the moment the password changes. Legacy tokens without the
            // claim resolve to 0 and match legacy accounts (also 0).
            if (userDetails instanceof AppUserDetails appUserDetails
                    && jwtService.extractTokenVersion(token) != appUserDetails.getTokenVersion()) {
                log.debug("JWT filter: stale token version rejected for path={}", path);
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }

            if (!userDetails.isEnabled()) {
                log.debug("JWT filter: disabled/banned account attempted access, path={}", path);
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, request.getRequestURI(), BANNED_MESSAGE);
                return;
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("JWT filter: authenticated user={}, path={}", userDetails.getUsername(), path);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws-notifications")
                || path.equals("/error");
    }

    private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"timestamp\":\"" + Instant.now() + "\","
                        + "\"status\":401,"
                        + "\"error\":\"UNAUTHORIZED\","
                        + "\"message\":\"" + message + "\","
                        + "\"path\":\"" + path + "\"}"
        );
    }


}
