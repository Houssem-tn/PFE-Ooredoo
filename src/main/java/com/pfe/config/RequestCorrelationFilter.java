package com.pfe.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns every request a correlation ID (never a user identifier —
 * purely a random/forwarded opaque token, see {@link #isValid}), puts it
 * in MDC so logback-spring.xml's JSON encoder attaches it to every log
 * line for the request, and echoes it back in the response so a client
 * or an upstream proxy (nginx.conf sets $request_id the same way — see
 * that file) can correlate its own logs with the backend's.
 *
 * <p>Registered with {@link Ordered#HIGHEST_PRECEDENCE} so the ID is in
 * MDC before any other filter (including {@link com.pfe.Security.JwtAuthFilter})
 * logs anything for this request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    // Accepts nginx's own $request_id (32 lowercase hex chars) and typical
    // UUIDs, rejects anything that looks like it could be an injection
    // attempt (log-forging via newlines, wildly long values, etc.) rather
    // than trusting an arbitrary client-supplied header verbatim.
    private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_NAME);
        String requestId = isValid(incoming) ? incoming : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused — an uncleared MDC entry would
            // leak this request's ID onto whatever the next request on the
            // same thread logs.
            MDC.remove(MDC_KEY);
        }
    }

    private boolean isValid(String value) {
        return value != null && VALID_ID.matcher(value).matches();
    }
}
