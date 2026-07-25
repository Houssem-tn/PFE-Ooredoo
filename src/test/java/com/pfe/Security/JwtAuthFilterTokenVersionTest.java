package com.pfe.Security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the session-invalidation behaviour of {@link JwtAuthFilter}: a JWT
 * whose embedded version is behind the live account (e.g. after a password
 * reset bumped {@code tokenVersion}) must NOT authenticate the request.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTokenVersionTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsServiceImpl userDetailsService;
    @Mock private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/stock/equipment");
        request.setRequestURI("/api/v1/stock/equipment");
        request.addHeader("Authorization", "Bearer some.jwt.token");
        return request;
    }

    private AppUserDetails userDetails(long tokenVersion) {
        return new AppUserDetails("user@example.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_STT")), tokenVersion);
    }

    @Test
    void staleTokenVersion_isNotAuthenticated() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
        when(jwtService.isValid("some.jwt.token")).thenReturn(true);
        when(jwtService.extractSubject("some.jwt.token")).thenReturn("user@example.com");
        when(jwtService.extractTokenVersion("some.jwt.token")).thenReturn(0L); // old token
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails(1L)); // bumped

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void currentTokenVersion_isAuthenticated() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
        when(jwtService.isValid("some.jwt.token")).thenReturn(true);
        when(jwtService.extractSubject("some.jwt.token")).thenReturn("user@example.com");
        when(jwtService.extractTokenVersion("some.jwt.token")).thenReturn(3L);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails(3L));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user@example.com");
        verify(chain).doFilter(any(), any());
    }
}
