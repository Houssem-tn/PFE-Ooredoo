package com.pfe.Security;

import com.pfe.MainPackage.Entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-at-least-32-characters-long";

    @Test
    void generateToken_thenExtractSubjectAndRole_roundTrips() {
        JwtService service = new JwtService(SECRET, 60_000L);

        String token = service.generateToken("zm@example.com", UserRole.ROLE_ZM);

        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractSubject(token)).isEqualTo("zm@example.com");
        assertThat(service.extractRole(token)).isEqualTo("ROLE_ZM");
    }

    @Test
    void isValid_shouldReturnFalseForTamperedToken() {
        JwtService service = new JwtService(SECRET, 60_000L);
        String token = service.generateToken("stt@example.com", UserRole.ROLE_STT);

        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseForGarbageToken() {
        JwtService service = new JwtService(SECRET, 60_000L);

        assertThat(service.isValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseForExpiredToken() throws InterruptedException {
        // 1ms expiry — deterministic without a sleep-based test elsewhere in the suite;
        // this is the one legitimate place a short wait is unavoidable (JJWT reads system time).
        JwtService service = new JwtService(SECRET, 1L);
        String token = service.generateToken("tech@example.com", UserRole.ROLE_TECH);

        Thread.sleep(15);

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    void tokensSignedWithDifferentSecrets_shouldNotValidateAgainstEachOther() {
        JwtService issuer = new JwtService(SECRET, 60_000L);
        JwtService otherIssuer = new JwtService("a-completely-different-secret-value-1234567890", 60_000L);

        String token = issuer.generateToken("admin@example.com", UserRole.ROLE_ADMIN);

        assertThat(otherIssuer.isValid(token)).isFalse();
    }
}
