package com.pfe.Security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link WebSocketAuthChannelInterceptor} rejects a STOMP CONNECT
 * carrying a JWT whose version is behind the live account — the WebSocket
 * counterpart of the REST session-invalidation check, exercised after a
 * password reset bumps {@code tokenVersion}.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthTokenVersionTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsServiceImpl userDetailsService;

    private Message<byte[]> connectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer ws.jwt.token");
        // Keep the accessor mutable so the interceptor can setUser() on accept,
        // mirroring the real broker relay's message construction.
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private AppUserDetails userDetails(long tokenVersion) {
        return new AppUserDetails("user@example.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_STT")), tokenVersion);
    }

    @Test
    void staleTokenVersion_isRejected() {
        WebSocketAuthChannelInterceptor interceptor =
                new WebSocketAuthChannelInterceptor(jwtService, userDetailsService);
        when(jwtService.isValid("ws.jwt.token")).thenReturn(true);
        when(jwtService.extractSubject("ws.jwt.token")).thenReturn("user@example.com");
        when(jwtService.extractTokenVersion("ws.jwt.token")).thenReturn(0L); // old token
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails(1L)); // bumped

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currentTokenVersion_isAccepted() {
        WebSocketAuthChannelInterceptor interceptor =
                new WebSocketAuthChannelInterceptor(jwtService, userDetailsService);
        when(jwtService.isValid("ws.jwt.token")).thenReturn(true);
        when(jwtService.extractSubject("ws.jwt.token")).thenReturn("user@example.com");
        when(jwtService.extractTokenVersion("ws.jwt.token")).thenReturn(5L);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails(5L));

        Message<byte[]> result = (Message<byte[]>) interceptor.preSend(connectMessage(), null);

        assertThat(result).isNotNull();
    }
}
