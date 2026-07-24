package com.pfe.Security;

import com.pfe.MainPackage.Entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    /** JWT claim carrying the user's tokenVersion at issue time (session-invalidation epoch). */
    public static final String CLAIM_TOKEN_VERSION = "ver";

    private final Key key;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret:dev-secret-change-me-dev-secret-change-me}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Backwards-compatible overload — issues a token pinned to tokenVersion 0.
     * Retained so pre-existing callers/tests keep compiling; production login
     * uses {@link #generateToken(String, UserRole, long)} to embed the user's
     * live version.
     */
    public String generateToken(String subjectEmail, UserRole role) {
        return generateToken(subjectEmail, role, 0L);
    }

    public String generateToken(String subjectEmail, UserRole role, long tokenVersion) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("role", role.name());
        claims.put(CLAIM_TOKEN_VERSION, tokenVersion);

        return Jwts.builder()
                .setSubject(subjectEmail)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .addClaims(claims)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Object role = parseClaims(token).get("role");
        return role != null ? role.toString() : null;
    }

    /**
     * Reads the tokenVersion embedded in the token. Tokens issued before this
     * claim existed (or any malformed value) resolve to 0, which matches the 0
     * that legacy users carry in {@code AppUser.tokenVersion} — so upgrading
     * never invalidates a currently valid session.
     */
    public long extractTokenVersion(String token) {
        Object version = parseClaims(token).get(CLAIM_TOKEN_VERSION);
        if (version instanceof Number number) {
            return number.longValue();
        }
        if (version != null) {
            try {
                return Long.parseLong(version.toString());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
