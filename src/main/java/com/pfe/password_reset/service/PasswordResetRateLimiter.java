package com.pfe.password_reset.service;

import com.pfe.password_reset.config.PasswordResetProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abuse protection for the password-reset endpoints. Enforces, per rolling hour:
 * a per-email cooldown + hourly cap on forgot-password, a per-IP hourly cap on
 * forgot-password, and a per-IP hourly cap on reset (token-submit) attempts to
 * blunt token brute-forcing.
 *
 * <h2>In-memory limitation (multi-replica / Kubernetes)</h2>
 * This limiter stores counters in this JVM's heap. In a multi-replica
 * deployment each pod keeps its own counters, so the effective global limit is
 * roughly {@code perPodLimit × replicaCount} and a client whose requests are
 * load-balanced across pods sees weaker enforcement. It is intentionally chosen
 * here because the project has no shared cache (Redis) wired in, and it still
 * provides meaningful per-pod protection and a clean seam. For strict global
 * limits across replicas, back these counters with a shared store (e.g. Redis
 * INCR + EXPIRE, or a Bucket4j distributed backend) behind this same interface —
 * no caller changes required. Keys are bounded and evicted as their windows
 * expire, so memory cannot grow without bound from unique emails/IPs.
 */
@Component
public class PasswordResetRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRateLimiter.class);
    private static final Duration WINDOW = Duration.ofHours(1);

    private final PasswordResetProperties properties;

    private final Map<String, Deque<Instant>> forgotByEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> forgotByIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> resetByIp = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter(PasswordResetProperties properties) {
        this.properties = properties;
    }

    /**
     * Records and evaluates a forgot-password attempt for the given normalized
     * email and (already hashed/opaque) IP key.
     *
     * @return {@code true} if the attempt is allowed, {@code false} if rate-limited.
     *         The caller MUST still return the identical generic response either
     *         way — a limited result is never surfaced to the client.
     */
    public synchronized boolean allowForgotPassword(String normalizedEmail, String ipKey) {
        Instant now = Instant.now();

        // Per-email cooldown: reject a burst before it even counts against the cap.
        Deque<Instant> emailHits = forgotByEmail.computeIfAbsent(normalizedEmail, k -> new ArrayDeque<>());
        prune(emailHits, now);
        Instant lastEmailHit = emailHits.peekLast();
        if (lastEmailHit != null
                && Duration.between(lastEmailHit, now).getSeconds() < properties.getRequestCooldownSeconds()) {
            return false;
        }
        if (emailHits.size() >= properties.getMaxRequestsPerEmailPerHour()) {
            return false;
        }

        if (ipKey != null && !ipKey.isBlank()) {
            Deque<Instant> ipHits = forgotByIp.computeIfAbsent(ipKey, k -> new ArrayDeque<>());
            prune(ipHits, now);
            if (ipHits.size() >= properties.getMaxRequestsPerIpPerHour()) {
                return false;
            }
            ipHits.addLast(now);
        }

        emailHits.addLast(now);
        evictEmptyWindows(now);
        return true;
    }

    /**
     * Records and evaluates a reset (token-submit) attempt for the given IP key.
     * @return {@code true} if allowed, {@code false} if the per-IP cap is exceeded.
     */
    public synchronized boolean allowResetAttempt(String ipKey) {
        if (ipKey == null || ipKey.isBlank()) {
            return true;
        }
        Instant now = Instant.now();
        Deque<Instant> hits = resetByIp.computeIfAbsent(ipKey, k -> new ArrayDeque<>());
        prune(hits, now);
        if (hits.size() >= properties.getMaxResetAttemptsPerIpPerHour()) {
            return false;
        }
        hits.addLast(now);
        return true;
    }

    private void prune(Deque<Instant> hits, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
            hits.pollFirst();
        }
    }

    /**
     * Bounds memory: drop map entries whose windows have fully drained. Runs on
     * the same synchronized path so it never races with mutation. Cheap because
     * pruned deques are already empty.
     */
    private void evictEmptyWindows(Instant now) {
        forgotByEmail.entrySet().removeIf(e -> {
            prune(e.getValue(), now);
            return e.getValue().isEmpty();
        });
        forgotByIp.entrySet().removeIf(e -> {
            prune(e.getValue(), now);
            return e.getValue().isEmpty();
        });
        resetByIp.entrySet().removeIf(e -> {
            prune(e.getValue(), now);
            return e.getValue().isEmpty();
        });
        if (log.isDebugEnabled()) {
            log.debug("password_reset_rate_limiter_state emails={} forgotIps={} resetIps={}",
                    forgotByEmail.size(), forgotByIp.size(), resetByIp.size());
        }
    }
}
